package com.dfs.service;

import com.dfs.model.StorageNode;
import com.dfs.repository.StorageNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LoadBalancerService — selects the optimal storage nodes for each file chunk.
 *
 * Strategy: Weighted Least-Connections + Zone Awareness
 * ─────────────────────────────────────────────────────
 *  1. Filter out OFFLINE / DRAINING nodes.
 *  2. Score each candidate by free-capacity weight.
 *  3. For replicas, prefer nodes in different availability zones
 *     to maximise fault-tolerance.
 *  4. Return an ordered list of [primary, replica-1, replica-2, …]
 *     node IDs of length == replicationFactor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoadBalancerService {

    private final StorageNodeRepository nodeRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Select {@code replicationFactor} distinct nodes for a single chunk.
     *
     * @param chunkSizeBytes  size of the chunk that must fit on each selected node
     * @param replicationFactor number of copies (primary + replicas)
     * @return ordered list: index 0 = primary node, rest = replica nodes
     * @throws IllegalStateException if there are not enough healthy nodes
     */
    public List<StorageNode> selectNodesForChunk(long chunkSizeBytes, int replicationFactor) {
        List<StorageNode> healthy = getHealthyNodesWithCapacity(chunkSizeBytes);

        if (healthy.size() < replicationFactor) {
            throw new IllegalStateException(String.format(
                "Not enough healthy nodes. Required: %d, Available: %d",
                replicationFactor, healthy.size()
            ));
        }

        // Sort candidates by ascending usage percentage (least-loaded first)
        healthy.sort(Comparator.comparingDouble(StorageNode::getUsagePercent));

        return selectWithZoneAwareness(healthy, replicationFactor);
    }

    /**
     * Returns the best available node that holds a replica of a specific chunk,
     * excluding any node IDs in the {@code excludedNodeIds} set (e.g., offline nodes).
     *
     * Used by the failover logic: if Node A is down, retrieve from Node B.
     *
     * @param candidateNodeIds all node IDs that hold this chunk (primary + replicas)
     * @param excludedNodeIds  nodes that are currently unreachable
     * @return the best healthy node among candidates, or empty if none available
     */
    public Optional<StorageNode> selectFailoverNode(
            List<String> candidateNodeIds,
            Set<String> excludedNodeIds) {

        return nodeRepository.findAllById(candidateNodeIds)
                .stream()
                .filter(n -> !excludedNodeIds.contains(n.getId()))
                .filter(StorageNode::isAvailable)
                .min(Comparator.comparingDouble(StorageNode::getUsagePercent));
    }

    /**
     * Returns all currently healthy nodes, sorted by availability score.
     * Used by the admin dashboard.
     */
    public List<StorageNode> getHealthyNodes() {
        return nodeRepository.findByStatus(StorageNode.NodeStatus.ONLINE)
                .stream()
                .filter(StorageNode::isAvailable)
                .sorted(Comparator.comparingDouble(StorageNode::getUsagePercent))
                .collect(Collectors.toList());
    }

    /**
     * Returns a snapshot of all nodes (for the admin panel).
     */
    public List<StorageNode> getAllNodes() {
        return nodeRepository.findAll();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Fetches all ONLINE nodes that have sufficient free capacity for the chunk.
     */
    private List<StorageNode> getHealthyNodesWithCapacity(long requiredBytes) {
        return nodeRepository.findByStatus(StorageNode.NodeStatus.ONLINE)
                .stream()
                .filter(StorageNode::isAvailable)
                .filter(n -> n.getAvailableCapacity() >= requiredBytes)
                .collect(Collectors.toList());
    }

    /**
     * Zone-aware selection algorithm:
     *
     *  - Pick the least-loaded node as the primary.
     *  - For each subsequent replica, prefer a node from a zone not yet used.
     *  - If all zones are exhausted, fall back to least-loaded from remaining pool.
     *
     * @param sortedByLoad  nodes pre-sorted least-loaded first
     * @param count         how many to select
     */
    private List<StorageNode> selectWithZoneAwareness(
            List<StorageNode> sortedByLoad, int count) {

        List<StorageNode> selected = new ArrayList<>(count);
        Set<String> usedZones = new HashSet<>();
        List<StorageNode> remaining = new ArrayList<>(sortedByLoad);

        while (selected.size() < count && !remaining.isEmpty()) {
            // Prefer a node from an unused zone
            Optional<StorageNode> zoneCandidate = remaining.stream()
                    .filter(n -> !usedZones.contains(n.getZone()))
                    .findFirst();   // list is already sorted by load

            StorageNode chosen = zoneCandidate.orElse(remaining.get(0));

            selected.add(chosen);
            usedZones.add(chosen.getZone());
            remaining.remove(chosen);
        }

        if (selected.size() < count) {
            throw new IllegalStateException(
                "Could not fill required replication factor after zone-aware selection. " +
                "Required: " + count + ", Selected: " + selected.size()
            );
        }

        log.debug("LoadBalancer selected nodes: {}",
                selected.stream().map(StorageNode::getId).collect(Collectors.joining(", ")));

        return selected;
    }
}
