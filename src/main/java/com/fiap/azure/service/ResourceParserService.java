package com.fiap.azure.service;
import com.fiap.azure.model.Resource;
import com.fiap.azure.model.ResourceGroup;
import com.fiap.azure.model.Subscription;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
@Slf4j
@Service
public class ResourceParserService {
    public List<Subscription> parseSubscriptions(JsonNode node) {
        List<Subscription> subscriptions = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                try {
                    String id = getTextOrDefault(item, "id", "unknown");
                    String name = getTextOrDefault(item, "name", "N/A");
                    String state = getTextOrDefault(item, "state", "Unknown");
                    String displayName = getTextOrDefault(item, "displayName", name);
                    Subscription sub = new Subscription(id, name, state, displayName);
                    subscriptions.add(sub);
                    log.debug("Assinatura parseada: {}", displayName);
                } catch (Exception e) {
                    log.warn("Erro ao parsear assinatura: {}", e.getMessage());
                }
            });
        }
        log.info("Parsed {} subscriptions", subscriptions.size());
        return subscriptions;
    }
    public List<ResourceGroup> parseResourceGroups(JsonNode node) {
        List<ResourceGroup> resourceGroups = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                try {
                    String id = getTextOrDefault(item, "id", "unknown");
                    String name = getTextOrDefault(item, "name", "N/A");
                    String location = getTextOrDefault(item, "location", "Unknown");
                    String provisioningState = "Unknown";
                    if (item.has("properties") && item.get("properties").has("provisioningState")) {
                        provisioningState = item.get("properties").get("provisioningState").asText();
                    }
                    ResourceGroup rg = new ResourceGroup(id, name, location, provisioningState, new ArrayList<>());
                    resourceGroups.add(rg);
                    log.debug("Grupo de Recursos parseado: {}", name);
                } catch (Exception e) {
                    log.warn("Erro ao parsear grupo de recursos: {}", e.getMessage());
                }
            });
        }
        log.info("Parsed {} resource groups", resourceGroups.size());
        return resourceGroups;
    }
    public List<ResourceGroup> parseResourcesInGroup(JsonNode node, String groupName) {
        List<ResourceGroup> result = new ArrayList<>();
        ResourceGroup rg = new ResourceGroup();
        rg.setName(groupName);
        rg.setResources(new ArrayList<>());
        if (node.isArray()) {
            node.forEach(item -> {
                try {
                    String id = getTextOrDefault(item, "id", "unknown");
                    String name = getTextOrDefault(item, "name", "N/A");
                    String type = getTextOrDefault(item, "type", "Unknown");
                    String location = getTextOrDefault(item, "location", "N/A");
                    String resourceGroup = getTextOrDefault(item, "resourceGroup", groupName);
                    Resource resource = new Resource(id, name, type, location, resourceGroup);
                    rg.getResources().add(resource);
                    log.debug("Recurso parseado: {}", name);
                } catch (Exception e) {
                    log.warn("Erro ao parsear recurso: {}", e.getMessage());
                }
            });
        }
        result.add(rg);
        log.info("Parsed {} resources in group {}", rg.getResources().size(), groupName);
        return result;
    }
    private String getTextOrDefault(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName)) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return defaultValue;
        }
        String value = field.asText();
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
