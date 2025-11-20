package com.fiap.azure.controller;

import com.fiap.azure.dto.ResourceGroupDTO;
import com.fiap.azure.dto.SubscriptionDTO;
import com.fiap.azure.exception.AzureCliException;
import com.fiap.azure.model.ResourceGroup;
import com.fiap.azure.model.Subscription;
import com.fiap.azure.service.AzureCliService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/")
public class ResourceController {

    private final AzureCliService azureCliService;

    @GetMapping
    public String dashboard(Model model, @RequestParam(required = false) String subscriptionId) {
        try {
            // Listar APENAS assinaturas com recursos
            List<Subscription> subscriptions = azureCliService.listSubscriptionsWithResources();
            List<SubscriptionDTO> subscriptionDTOs = subscriptions.stream()
                    .map(this::toSubscriptionDTO)
                    .collect(Collectors.toList());

            model.addAttribute("subscriptions", subscriptionDTOs);
            model.addAttribute("totalSubscriptions", subscriptionDTOs.size());

            List<ResourceGroup> resourceGroups = new ArrayList<>();
            if (subscriptionId != null && !subscriptionId.isEmpty()) {
                log.info("Carregando recursos da assinatura: {}", subscriptionId);
                try {
                    resourceGroups = azureCliService.listResourcesBySubscription(subscriptionId);
                    model.addAttribute("currentSubscriptionId", subscriptionId);
                } catch (AzureCliException e) {
                    log.warn("Erro ao carregar recursos: {}", e.getMessage());
                    model.addAttribute("warning", "Erro ao carregar recursos: " + e.getMessage());
                }
            } else {
                log.info("Nenhuma assinatura selecionada");
            }

            List<ResourceGroupDTO> resourceGroupDTOs = resourceGroups.stream()
                    .map(this::toResourceGroupDTO)
                    .collect(Collectors.toList());

            model.addAttribute("resourceGroups", resourceGroupDTOs);
            model.addAttribute("totalGroups", resourceGroupDTOs.size());

            log.info("Dashboard carregado com {} assinaturas (com recursos) e {} grupos", 
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
        try {
            azureCliService.deleteResourceGroup(groupName, subscriptionId);
            log.info("Grupo deletado: {}", groupName);
            return ResponseEntity.ok("{\"status\": \"success\", \"message\": \"Grupo deletado com sucesso\"}");
        } catch (AzureCliException e) {
            log.error("Erro ao deletar grupo", e);
            return ResponseEntity.status(500)
                    .body("{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/api/delete-groups")
    @ResponseBody
    public ResponseEntity<?> deleteResourceGroups(@RequestBody DeleteGroupsRequest request) {
        try {
            for (String groupName : request.getGroupNames()) {
                azureCliService.deleteResourceGroup(groupName, request.getSubscriptionId());
            }
            log.info("Deletados {} grupos", request.getGroupNames().size());
            return ResponseEntity.ok("{\"status\": \"success\", \"message\": \"Grupos deletados com sucesso\"}");
        } catch (AzureCliException e) {
            log.error("Erro ao deletar grupos", e);
            return ResponseEntity.status(500)
                    .body("{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/api/delete-all-subscription")
    @ResponseBody
    public ResponseEntity<?> deleteAllResourceGroupsSubscription(@RequestParam String subscriptionId) {
        try {
            azureCliService.deleteAllResourceGroupsBySubscription(subscriptionId);
            log.info("Todos os grupos da assinatura {} deletados", subscriptionId);
            return ResponseEntity.ok("{\"status\": \"success\", \"message\": \"Todos os grupos deletados com sucesso\"}");
        } catch (AzureCliException e) {
            log.error("Erro ao deletar todos os grupos", e);
            return ResponseEntity.status(500)
                    .body("{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/api/delete-all-all")
    @ResponseBody
    public ResponseEntity<?> deleteAllResourceGroupsAllSubscriptions(@RequestBody List<String> subscriptionIds) {
        try {
            azureCliService.deleteAllResourceGroupsAllSubscriptions(subscriptionIds);
            log.info("Todos os grupos de todas as assinaturas deletados");
            return ResponseEntity.ok("{\"status\": \"success\", \"message\": \"Todos os grupos de todas as assinaturas deletados com sucesso\"}");
        } catch (AzureCliException e) {
            log.error("Erro ao deletar todos os grupos", e);
            return ResponseEntity.status(500)
                    .body("{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
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

    public static class DeleteGroupsRequest {
        private List<String> groupNames;
        private String subscriptionId;

        public List<String> getGroupNames() { return groupNames; }
        public void setGroupNames(List<String> groupNames) { this.groupNames = groupNames; }
        
        public String getSubscriptionId() { return subscriptionId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    }
}
