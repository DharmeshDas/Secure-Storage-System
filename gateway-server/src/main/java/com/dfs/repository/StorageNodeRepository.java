package com.dfs.repository;

import com.dfs.model.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StorageNodeRepository extends JpaRepository<StorageNode, String> {

    List<StorageNode> findByStatus(StorageNode.NodeStatus status);

    @Query("SELECT n FROM StorageNode n WHERE n.status = 'ONLINE' " +
           "AND n.lastHeartbeat > :cutoff " +
           "ORDER BY (n.usedCapacity * 1.0 / NULLIF(n.totalCapacity, 0)) ASC")
    List<StorageNode> findHealthyNodesSortedByLoad(
            @Param("cutoff") LocalDateTime cutoff);

    // Delegated from NodeHealthMonitor — wraps the FileMetadataRepository query
    // via a native call so the monitor doesn't need to inject two repos.
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM files WHERE deleted_at IS NOT NULL " +
                   "AND deleted_at < DATE_SUB(NOW(), INTERVAL 30 DAY)",
           nativeQuery = true)
    int purgeExpiredSoftDeletes();
}
