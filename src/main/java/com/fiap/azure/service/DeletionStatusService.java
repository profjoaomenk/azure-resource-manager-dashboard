package com.fiap.azure.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeletionStatusService {

    private final Map<String, DeletionStatus> statusMap = new ConcurrentHashMap<>();

    public void markAsDeleting(String groupName, String subscriptionId) {
        statusMap.put(groupName, new DeletionStatus(
            groupName,
            subscriptionId,
            "DELETING",
            null,
            LocalDateTime.now()
        ));
        log.info("📌 Marcado como deletando: {}", groupName);
    }

    public void markAsCompleted(String groupName, boolean success, String message) {
        DeletionStatus current = statusMap.get(groupName);
        if (current != null) {
            statusMap.put(groupName, new DeletionStatus(
                groupName,
                current.subscriptionId(),
                success ? "COMPLETED" : "FAILED",
                message,
                current.startedAt()
            ));
        }
        log.info("📌 Status atualizado: {} -> {}", groupName, success ? "COMPLETED" : "FAILED");
        
        // Remove após 30 segundos se completado
        if (success) {
            new Thread(() -> {
                try {
                    Thread.sleep(30000);
                    statusMap.remove(groupName);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    public DeletionStatus getStatus(String groupName) {
        return statusMap.get(groupName);
    }

    public Map<String, DeletionStatus> getAllStatus() {
        return Map.copyOf(statusMap);
    }

    public Set<String> getDeletingGroups() {
        return statusMap.entrySet().stream()
            .filter(e -> "DELETING".equals(e.getValue().status()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    public boolean isDeleting(String groupName) {
        DeletionStatus status = statusMap.get(groupName);
        return status != null && "DELETING".equals(status.status());
    }

    public record DeletionStatus(
        String groupName,
        String subscriptionId,
        String status,
        String message,
        LocalDateTime startedAt
    ) {}
}
