package com.fiap.azure.service;

import com.fiap.azure.exception.AzureCliException;
import com.fiap.azure.model.Resource;
import com.fiap.azure.model.ResourceGroup;
import com.fiap.azure.model.Subscription;
import com.fiap.azure.util.AzureCommandExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureCliService {

    private final AzureCommandExecutor commandExecutor;
    private final ObjectMapper objectMapper;
    private final ResourceParserService parserService;
    private final AzureAuthService authService;
    
    private static final Map<String, CacheEntry> subscriptionCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 300000;

    public List<Subscription> listSubscriptions() throws AzureCliException {
        log.info("Listando todas as assinaturas do Azure");
        authService.ensureAuthenticated();
        try {
            String output = commandExecutor.execute("account", "list", "--output", "json");
            JsonNode node = objectMapper.readTree(output);
            List<Subscription> allSubscriptions = parserService.parseSubscriptions(node);
            log.info("Total de assinaturas: {}", allSubscriptions.size());
            return allSubscriptions;
        } catch (IOException e) {
            log.error("Erro ao parsear assinaturas", e);
            throw new AzureCliException("Erro ao parsear assinaturas", e);
        }
    }

    public List<Subscription> listSubscriptionsWithResources() throws AzureCliException {
        log.info("Listando assinaturas ATIVAS COM RECURSOS");
        long startTime = System.currentTimeMillis();
        
        List<Subscription> allSubscriptions = listSubscriptions();
        List<Subscription> subscriptionsWithResources = new ArrayList<>();

        for (Subscription sub : allSubscriptions) {
            try {
                // Filtrar apenas assinaturas ATIVAS
                if (!"Enabled".equals(sub.getState())) {
                    log.info("⏭️  Assinatura {} não está ativa ({})", sub.getDisplayName(), sub.getState());
                    continue;
                }
                
                // Verificação rápida: só contar se tem grupos
                long groupCount = countResourceGroupsFast(sub.getId());
                
                if (groupCount > 0) {
                    subscriptionsWithResources.add(sub);
                    log.info("✅ Assinatura {} tem grupos", sub.getDisplayName());
                } else {
                    log.info("⏭️  Assinatura {} sem recursos", sub.getDisplayName());
                }
            } catch (Exception e) {
                log.warn("⏭️  Erro ao verificar assinatura {}: {}", sub.getDisplayName(), e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("⚡ Assinaturas ATIVAS com recursos: {} (tempo: {}ms)", subscriptionsWithResources.size(), duration);
        return subscriptionsWithResources;
    }

    private long countResourceGroupsFast(String subscriptionId) throws AzureCliException {
        try {
            String cacheKey = "groups_" + subscriptionId;
            CacheEntry cached = subscriptionCache.get(cacheKey);
            
            if (cached != null && !cached.isExpired()) {
                log.debug("📦 Cache hit para grupos: {}", subscriptionId);
                return cached.groupCount;
            }
            
            String output = commandExecutor.execute("group", "list", 
                    "--subscription", subscriptionId,
                    "--output", "json");
            JsonNode node = objectMapper.readTree(output);
            
            long count = 0;
            if (node.isArray()) {
                count = node.size();
            }
            
            subscriptionCache.put(cacheKey, new CacheEntry(count, 0));
            
            return count;
        } catch (Exception e) {
            log.debug("Erro ao contar grupos para {}: {}", subscriptionId, e.getMessage());
            return 0;
        }
    }

    public List<ResourceGroup> listResourceGroups() throws AzureCliException {
        log.info("Listando grupos de recursos");
        authService.ensureAuthenticated();
        try {
            String output = commandExecutor.execute("group", "list", "--output", "json");
            JsonNode node = objectMapper.readTree(output);
            return parserService.parseResourceGroups(node);
        } catch (IOException e) {
            log.error("Erro ao parsear grupos de recursos", e);
            throw new AzureCliException("Erro ao parsear grupos de recursos", e);
        }
    }

    public List<ResourceGroup> listResourcesBySubscription(String subscriptionId) throws AzureCliException {
        log.info("Listando grupos de recursos da assinatura: {}", subscriptionId);
        long startTime = System.currentTimeMillis();
        authService.ensureAuthenticated();
        
        try {
            String output = commandExecutor.execute("group", "list", 
                    "--subscription", subscriptionId,
                    "--output", "json");
            JsonNode node = objectMapper.readTree(output);
            List<ResourceGroup> resourceGroups = parserService.parseResourceGroups(node);
            
            resourceGroups.parallelStream().forEach(rg -> {
                try {
                    List<Resource> resources = listResourcesInGroupInternal(rg.getName(), subscriptionId);
                    rg.setResources(resources);
                    log.debug("Grupo {} tem {} recursos", rg.getName(), resources.size());
                } catch (Exception e) {
                    log.warn("Erro ao listar recursos do grupo {}: {}", rg.getName(), e.getMessage());
                    rg.setResources(new ArrayList<>());
                }
            });
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("⚡ {} grupos carregados em {}ms", resourceGroups.size(), duration);
            return resourceGroups;
        } catch (IOException e) {
            log.error("Erro ao parsear grupos de recursos", e);
            throw new AzureCliException("Erro ao parsear grupos de recursos", e);
        }
    }

    private List<Resource> listResourcesInGroupInternal(String resourceGroupName, String subscriptionId) throws AzureCliException {
        try {
            String output = commandExecutor.execute("resource", "list", 
                    "--resource-group", resourceGroupName,
                    "--subscription", subscriptionId,
                    "--output", "json");
            JsonNode node = objectMapper.readTree(output);
            
            List<Resource> resources = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(item -> {
                    try {
                        String id = item.has("id") ? item.get("id").asText() : "unknown";
                        String name = item.has("name") ? item.get("name").asText() : "N/A";
                        String type = item.has("type") ? item.get("type").asText() : "Unknown";
                        String location = item.has("location") ? item.get("location").asText() : "N/A";
                        
                        Resource resource = new Resource(id, name, type, location, resourceGroupName);
                        resources.add(resource);
                    } catch (Exception e) {
                        log.warn("Erro ao parsear recurso: {}", e.getMessage());
                    }
                });
            }
            
            return resources;
        } catch (IOException e) {
            log.error("Erro ao parsear recursos", e);
            throw new AzureCliException("Erro ao parsear recursos", e);
        }
    }

    public void deleteResourceGroup(String resourceGroupName, String subscriptionId) throws AzureCliException {
        log.info("Deletando grupo de recursos: {}", resourceGroupName);
        authService.ensureAuthenticated();
        try {
            commandExecutor.execute("group", "delete", 
                    "--name", resourceGroupName,
                    "--subscription", subscriptionId,
                    "--yes");
            
            String cacheKey = "groups_" + subscriptionId;
            subscriptionCache.remove(cacheKey);
            
            log.info("✅ Grupo deletado com sucesso");
        } catch (Exception e) {
            log.error("Erro ao deletar grupo de recursos", e);
            throw new AzureCliException("Erro ao deletar grupo de recursos: " + resourceGroupName, e);
        }
    }

    public void deleteAllResourceGroupsBySubscription(String subscriptionId) throws AzureCliException {
        log.info("Deletando TODOS os grupos da assinatura: {}", subscriptionId);
        authService.ensureAuthenticated();
        try {
            List<ResourceGroup> groups = listResourcesBySubscription(subscriptionId);
            for (ResourceGroup group : groups) {
                commandExecutor.execute("group", "delete", 
                        "--name", group.getName(),
                        "--subscription", subscriptionId,
                        "--yes");
            }
            
            String cacheKey = "groups_" + subscriptionId;
            subscriptionCache.remove(cacheKey);
            
            log.info("✅ Deletados {} grupos", groups.size());
        } catch (Exception e) {
            log.error("Erro ao deletar grupos", e);
            throw new AzureCliException("Erro ao deletar grupos de recursos", e);
        }
    }

    public void deleteAllResourceGroupsAllSubscriptions(List<String> subscriptionIds) throws AzureCliException {
        log.info("Deletando grupos de {} assinaturas", subscriptionIds.size());
        for (String subId : subscriptionIds) {
            try {
                deleteAllResourceGroupsBySubscription(subId);
            } catch (Exception e) {
                log.warn("Erro ao deletar grupos da assinatura {}: {}", subId, e.getMessage());
            }
        }
    }

    private static class CacheEntry {
        long groupCount;
        long resourceCount;
        long timestamp;
        
        CacheEntry(long groupCount, long resourceCount) {
            this.groupCount = groupCount;
            this.resourceCount = resourceCount;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_DURATION_MS;
        }
    }
}
