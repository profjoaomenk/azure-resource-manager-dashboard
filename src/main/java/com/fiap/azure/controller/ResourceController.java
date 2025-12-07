package com.fiap.azure.controller;

import com.fiap.azure.dto.ResourceGroupDTO;
import com.fiap.azure.dto.SubscriptionDTO;
import com.fiap.azure.exception.AzureCliException;
import com.fiap.azure.model.ResourceGroup;
import com.fiap.azure.model.Subscription;
import com.fiap.azure.service.AzureCliService;
import com.fiap.azure.service.DeletionStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/")
public class ResourceController {

    private final AzureCliService azureCliService;
    private final DeletionStatusService deletionStatusService;

    @GetMapping
    public String dashboard(Model model, @RequestParam(required = false) String subscriptionId) {
        try {
            List<Subscription> subscriptions = azureCliService.listSubscriptionsWithResources();
            List<SubscriptionDTO> subscriptionDTOs = subscriptions.stream()
                    .map(this::toSubscriptionDTO)
                    .collect(Collectors.toList());

            model.addAttribute("subscriptions", subscriptionDTOs);
            model.addAttribute("totalSubscriptions", subscriptionDTOs.size());

            String currentSubscriptionName = null;
            if (subscriptionId != null && !subscriptionId.isEmpty()) {
                currentSubscriptionName = subscriptionDTOs.stream()
                        .filter(s -> s.getId().equals(subscriptionId))
                        .map(SubscriptionDTO::getDisplayName)
                        .findFirst()
                        .orElse(null);
            }
            model.addAttribute("currentSubscriptionId", subscriptionId);
            model.addAttribute("currentSubscriptionName", currentSubscriptionName);

            List<ResourceGroup> resourceGroups = new ArrayList<>();
            if (subscriptionId != null && !subscriptionId.isEmpty()) {
                log.info("Carregando recursos da assinatura: {}", subscriptionId);
                try {
                    resourceGroups = azureCliService.listResourcesBySubscription(subscriptionId);
                } catch (AzureCliException e) {
                    log.warn("Erro ao carregar recursos: {}", e.getMessage());
                    model.addAttribute("warning", "Erro ao carregar recursos: " + e.getMessage());
                }
            }

            List<ResourceGroupDTO> resourceGroupDTOs = resourceGroups.stream()
                    .map(this::toResourceGroupDTO)
                    .collect(Collectors.toList());

            model.addAttribute("resourceGroups", resourceGroupDTOs);
            model.addAttribute("totalGroups", resourceGroupDTOs.size());
            model.addAttribute("deletingGroups", deletionStatusService.getDeletingGroups());

            log.info("Dashboard carregado com {} assinaturas e {} grupos", 
                    subscriptionDTOs.size(), resourceGroupDTOs.size());

        } catch (AzureCliException e) {
            log.error("Erro ao carregar dashboard", e);
            model.addAttribute("error", "Erro ao conectar com Azure: " + e.getMessage());
        }

        return "index";
    }

    @PostMapping("/api/delete-group")
    @ResponseBody
    public ResponseEntity<?> deleteResourceGroup(@RequestParam String groupName, @RequestParam String subscriptionId) {
        String taskId = UUID.randomUUID().toString();
        
        deletionStatusService.markAsDeleting(groupName, subscriptionId);
        
        CompletableFuture.runAsync(() -> {
            try {
                azureCliService.deleteResourceGroup(groupName, subscriptionId);
                deletionStatusService.markAsCompleted(groupName, true, "Deletado com sucesso");
                log.info("✅ Grupo deletado em background: {}", groupName);
            } catch (Exception e) {
                deletionStatusService.markAsCompleted(groupName, false, e.getMessage());
                log.error("❌ Erro ao deletar grupo em background: {}", groupName, e);
            }
        });
        
        log.info("🚀 Deleção iniciada em background: {}", groupName);
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "taskId", taskId,
            "groupName", groupName,
            "message", "Deleção iniciada em segundo plano"
        ));
    }

    @PostMapping("/api/delete-groups")
    @ResponseBody
    public ResponseEntity<?> deleteResourceGroups(@RequestBody DeleteGroupsRequest request) {
        String taskId = UUID.randomUUID().toString();
        
        for (String groupName : request.getGroupNames()) {
            deletionStatusService.markAsDeleting(groupName, request.getSubscriptionId());
        }
        
        CompletableFuture.runAsync(() -> {
            for (String groupName : request.getGroupNames()) {
                try {
                    azureCliService.deleteResourceGroup(groupName, request.getSubscriptionId());
                    deletionStatusService.markAsCompleted(groupName, true, "Deletado com sucesso");
                    log.info("✅ Grupo deletado: {}", groupName);
                } catch (Exception e) {
                    deletionStatusService.markAsCompleted(groupName, false, e.getMessage());
                    log.error("❌ Erro ao deletar: {}", groupName, e);
                }
            }
        });
        
        log.info("🚀 Deleção de {} grupos iniciada em background", request.getGroupNames().size());
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "taskId", taskId,
            "count", request.getGroupNames().size(),
            "message", "Deleção iniciada em segundo plano"
        ));
    }

    @PostMapping("/api/delete-all-subscription")
    @ResponseBody
    public ResponseEntity<?> deleteAllResourceGroupsSubscription(@RequestParam String subscriptionId) {
        String taskId = UUID.randomUUID().toString();
        
        CompletableFuture.runAsync(() -> {
            try {
                List<ResourceGroup> groups = azureCliService.listResourcesBySubscription(subscriptionId);
                for (ResourceGroup group : groups) {
                    deletionStatusService.markAsDeleting(group.getName(), subscriptionId);
                }
                for (ResourceGroup group : groups) {
                    try {
                        azureCliService.deleteResourceGroup(group.getName(), subscriptionId);
                        deletionStatusService.markAsCompleted(group.getName(), true, "Deletado");
                    } catch (Exception e) {
                        deletionStatusService.markAsCompleted(group.getName(), false, e.getMessage());
                    }
                }
                log.info("✅ Todos os grupos da assinatura {} deletados", subscriptionId);
            } catch (Exception e) {
                log.error("❌ Erro ao deletar grupos da assinatura", e);
            }
        });
        
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "taskId", taskId,
            "message", "Deleção de todos os grupos iniciada em segundo plano"
        ));
    }

    @PostMapping("/api/delete-all-all")
    @ResponseBody
    public ResponseEntity<?> deleteAllResourceGroupsAllSubscriptions(@RequestBody DeleteAllRequest request) {
        String taskId = UUID.randomUUID().toString();
        
        List<String> excludePatterns = request.getExcludePatterns() != null ? request.getExcludePatterns() : new ArrayList<>();
        String matchMode = request.getMatchMode() != null ? request.getMatchMode() : "exact";
        
        log.info("🚀 Iniciando deleção de todas as assinaturas. Exclusões: {} (modo: {})", excludePatterns, matchMode);
        
        CompletableFuture.runAsync(() -> {
            int totalDeleted = 0;
            int totalSkipped = 0;
            
            for (String subId : request.getSubscriptionIds()) {
                try {
                    List<ResourceGroup> groups = azureCliService.listResourcesBySubscription(subId);
                    
                    for (ResourceGroup group : groups) {
                        if (shouldExcludeGroup(group.getName(), excludePatterns, matchMode)) {
                            log.info("🛡️ Grupo PRESERVADO (filtro): {}", group.getName());
                            totalSkipped++;
                            continue;
                        }
                        deletionStatusService.markAsDeleting(group.getName(), subId);
                    }
                    
                    for (ResourceGroup group : groups) {
                        if (shouldExcludeGroup(group.getName(), excludePatterns, matchMode)) {
                            continue;
                        }
                        try {
                            azureCliService.deleteResourceGroup(group.getName(), subId);
                            deletionStatusService.markAsCompleted(group.getName(), true, "Deletado");
                            totalDeleted++;
                        } catch (Exception e) {
                            deletionStatusService.markAsCompleted(group.getName(), false, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro na assinatura {}: {}", subId, e.getMessage());
                }
            }
            log.info("✅ Deleção concluída. Deletados: {}, Preservados: {}", totalDeleted, totalSkipped);
        });
        
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "taskId", taskId,
            "excludeCount", excludePatterns.size(),
            "message", "Deleção iniciada em segundo plano"
        ));
    }

    private boolean shouldExcludeGroup(String groupName, List<String> patterns, String matchMode) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        
        String lowerGroupName = groupName.toLowerCase();
        
        for (String pattern : patterns) {
            String lowerPattern = pattern.toLowerCase().trim();
            if (lowerPattern.isEmpty()) continue;
            
            switch (matchMode) {
                case "exact":
                    if (lowerGroupName.equals(lowerPattern)) return true;
                    break;
                case "contains":
                    if (lowerGroupName.contains(lowerPattern)) return true;
                    break;
                case "startsWith":
                    if (lowerGroupName.startsWith(lowerPattern)) return true;
                    break;
            }
        }
        return false;
    }

    @GetMapping("/api/deletion-status")
    @ResponseBody
    public ResponseEntity<?> getDeletionStatus() {
        return ResponseEntity.ok(deletionStatusService.getAllStatus());
    }

    @GetMapping("/api/deletion-status/{groupName}")
    @ResponseBody
    public ResponseEntity<?> getDeletionStatus(@PathVariable String groupName) {
        return ResponseEntity.ok(deletionStatusService.getStatus(groupName));
    }

    private SubscriptionDTO toSubscriptionDTO(Subscription subscription) {
        return SubscriptionDTO.builder()
                .id(subscription.getId())
                .name(subscription.getName())
                .displayName(subscription.getDisplayName())
                .state(subscription.getState())
                .build();
    }

    private ResourceGroupDTO toResourceGroupDTO(ResourceGroup resourceGroup) {
        return ResourceGroupDTO.builder()
                .id(resourceGroup.getId())
                .name(resourceGroup.getName())
                .location(resourceGroup.getLocation())
                .provisioningState(resourceGroup.getProvisioningState())
                .resourceCount(resourceGroup.getResources() != null ? 
                        resourceGroup.getResources().size() : 0)
                .resources(resourceGroup.getResources() != null ? 
                        resourceGroup.getResources() : new ArrayList<>())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    // REQUEST CLASSES
    // ════════════════════════════════════════════════════════════════════════

    public static class DeleteGroupsRequest {
        private List<String> groupNames;
        private String subscriptionId;

        public List<String> getGroupNames() { return groupNames; }
        public void setGroupNames(List<String> groupNames) { this.groupNames = groupNames; }
        
        public String getSubscriptionId() { return subscriptionId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    }

    public static class DeleteAllRequest {
        private List<String> subscriptionIds;
        private List<String> excludePatterns;
        private String matchMode;

        public List<String> getSubscriptionIds() { return subscriptionIds; }
        public void setSubscriptionIds(List<String> subscriptionIds) { this.subscriptionIds = subscriptionIds; }

        public List<String> getExcludePatterns() { return excludePatterns; }
        public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }

        public String getMatchMode() { return matchMode; }
        public void setMatchMode(String matchMode) { this.matchMode = matchMode; }
    }
}
