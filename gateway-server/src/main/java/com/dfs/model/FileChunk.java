package com.dfs.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FileChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @Column(name = "node_id", nullable = false, length = 50)
    private String nodeId;

    @Column(name = "chunk_order", nullable = false)
    private Integer chunkOrder;

    @Column(name = "chunk_size", nullable = false)
    private Integer chunkSize;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "is_replica", nullable = false)
    @Builder.Default
    private boolean isReplica = false;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
