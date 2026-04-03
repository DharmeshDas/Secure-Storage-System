package com.dfs.repository;

import com.dfs.model.FileChunk;
import com.dfs.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, Long> {

    List<FileChunk> findByFileOrderByChunkOrder(FileMetadata file);

    @Query("SELECT c FROM FileChunk c WHERE c.file = :file AND c.chunkOrder = :order")
    List<FileChunk> findAllCopiesByFileAndChunkOrder(
            @Param("file")  FileMetadata file,
            @Param("order") int chunkOrder);

    List<FileChunk> findByNodeId(String nodeId);

    @Query("SELECT COUNT(c) FROM FileChunk c WHERE c.nodeId = :nodeId AND c.isReplica = false")
    long countPrimaryChunksByNode(@Param("nodeId") String nodeId);
}
