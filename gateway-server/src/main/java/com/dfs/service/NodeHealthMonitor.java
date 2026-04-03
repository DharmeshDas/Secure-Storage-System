package com.dfs.service;

import com.dfs.model.StorageNode;
import com.dfs.repository.StorageNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * NodeHealthMonitor — periodically pings every storage node's /actuator/health
 * endpoint and updates the node status in the database.
 *
 * Also runs the soft-delete purge job daily.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NodeHealthMonitor {

    private final StorageNodeRepository nodeRepository;
    private final RestTemplate restTemplate;

    // ── Health checks ─────────────────────────────────────────────────────────

    @Scheduled(fixedRateString = "${storage.health-check-interval:30000}")
    public void checkAllNodes() {
        List<StorageNode> nodes = nodeRepository.findAll();
        nodes.forEach(this::checkNode);
        log.debug("Health check completed for {} nodes", nodes.size());
    }

    @SuppressWarnings("unchecked")
    private void checkNode(StorageNode node) {
        String healthUrl = node.getBaseUrl() + "/actuator/health";
        try {
            Map<String, Object> health = restTemplate.getForObject(
                    healthUrl, Map.class);

            boolean up = health != null && "UP".equals(health.get("status"));

            if (up) {
                node.setStatus(StorageNode.NodeStatus.ONLINE);
                node.setLastHeartbeat(LocalDateTime.now());

                // Try to read capacity stats
                refreshNodeCapacity(node);
            } else {
                markOffline(node);
            }

        } catch (Exception e) {
            log.warn("Node {} health check failed: {}", node.getId(), e.getMessage());
            markOffline(node);
        }

        nodeRepository.save(node);
    }

    @SuppressWarnings("unchecked")
    private void refreshNodeCapacity(StorageNode node) {
        try {
            String statsUrl = node.getBaseUrl() + "/api/node/stats";
            Map<String, Object> stats = restTemplate.getForObject(statsUrl, Map.class);
            if (stats != null) {
                if (stats.containsKey("totalCapacity")) {
                    node.setTotalCapacity(((Number) stats.get("totalCapacity")).longValue());
                }
                if (stats.containsKey("usedCapacity")) {
                    node.setUsedCapacity(((Number) stats.get("usedCapacity")).longValue());
                }
            }
        } catch (Exception e) {
            log.debug("Could not refresh capacity for node {}: {}", node.getId(), e.getMessage());
        }
    }

    private void markOffline(StorageNode node) {
        if (node.getStatus() != StorageNode.NodeStatus.OFFLINE) {
            log.warn("Node {} is now OFFLINE", node.getId());
        }
        node.setStatus(StorageNode.NodeStatus.OFFLINE);
    }

    // ── Soft-delete purge ─────────────────────────────────────────────────────

    /** Runs every day at 02:00 AM. Purges expired soft-deleted files. */
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeExpiredSoftDeletes() {
        int deleted = nodeRepository.purgeExpiredSoftDeletes();
        log.info("Purge job: removed {} expired soft-deleted files", deleted);
    }
}
