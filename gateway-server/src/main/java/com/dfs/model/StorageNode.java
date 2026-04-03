package com.dfs.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "storage_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class StorageNode {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "zone", nullable = false, length = 50)
    @Builder.Default
    private String zone = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NodeStatus status = NodeStatus.ONLINE;

    @Column(name = "total_capacity", nullable = false)
    @Builder.Default
    private Long totalCapacity = 0L;

    @Column(name = "used_capacity", nullable = false)
    @Builder.Default
    private Long usedCapacity = 0L;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isAvailable() {
        return status == NodeStatus.ONLINE &&
               lastHeartbeat != null &&
               lastHeartbeat.isAfter(LocalDateTime.now().minusMinutes(2));
    }

    public double getUsagePercent() {
        if (totalCapacity == 0) return 100.0;
        return (double) usedCapacity / totalCapacity * 100.0;
    }

    public long getAvailableCapacity() {
        return totalCapacity - usedCapacity;
    }

    public enum NodeStatus {
        ONLINE, OFFLINE, DRAINING
    }
}
