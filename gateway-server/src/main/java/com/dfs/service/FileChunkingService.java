package com.dfs.service;

import com.dfs.config.StorageProperties;
import com.dfs.dto.ChunkUploadResult;
import com.dfs.model.*;
import com.dfs.repository.FileChunkRepository;
import com.dfs.repository.FileMetadataRepository;
import com.dfs.repository.StorageNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FileChunkingService — handles the full lifecycle of chunked file storage:
 *
 *  UPLOAD  → split file into 1 MB chunks → distribute to N nodes (replication)
 *  DOWNLOAD → fetch all chunk primaries (with failover) → reassemble byte stream
 *  DELETE   → soft-delete (mark deleted_at) or hard-delete after 30 days
 *  RESTORE  → clear deleted_at when within retention window
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileChunkingService {

    private final StorageProperties storageProperties;
    private final LoadBalancerService loadBalancerService;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileChunkRepository fileChunkRepository;
    private final StorageNodeRepository storageNodeRepository;
    private final RestTemplate restTemplate;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Accepts a raw multipart file, chunks it into storageProperties.chunkSizeBytes
     * pieces, distributes each chunk across replicationFactor nodes, and persists
     * the metadata.
     *
     * @return the saved FileMetadata record (status = COMPLETE on success)
     */
    @Transactional
    public FileMetadata uploadFile(MultipartFile file, User owner) throws IOException {

        int chunkSize    = storageProperties.getChunkSizeBytes();
        int replication  = storageProperties.getReplicationFactor();
        byte[] content   = file.getBytes();
        int totalChunks  = (int) Math.ceil((double) content.length / chunkSize);

        // Build skeleton metadata
        String storedName = UUID.randomUUID().toString();
        FileMetadata metadata = FileMetadata.builder()
                .owner(owner)
                .originalName(file.getOriginalFilename())
                .storedName(storedName)
                .mimeType(Optional.ofNullable(file.getContentType())
                        .orElse("application/octet-stream"))
                .fileSize((long) content.length)
                .totalChunks(totalChunks)
                .checksum(sha256Hex(content))
                .uploadStatus(FileMetadata.UploadStatus.UPLOADING)
                .build();

        metadata = fileMetadataRepository.save(metadata);

        try {
            List<FileChunk> allChunks = new ArrayList<>();

            for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                int start = chunkIndex * chunkSize;
                int end   = Math.min(start + chunkSize, content.length);
                byte[] chunkData = Arrays.copyOfRange(content, start, end);

                String chunkChecksum = sha256Hex(chunkData);

                // Select target nodes for this chunk (zone-aware, least-loaded)
                List<StorageNode> targetNodes = loadBalancerService
                        .selectNodesForChunk(chunkData.length, replication);

                for (int replica = 0; replica < targetNodes.size(); replica++) {
                    StorageNode node = targetNodes.get(replica);
                    boolean isPrimary = (replica == 0);

                    ChunkUploadResult result = uploadChunkToNode(
                            node, metadata.getStoredName(), chunkIndex, chunkData);

                    FileChunk chunk = FileChunk.builder()
                            .file(metadata)
                            .nodeId(node.getId())
                            .chunkOrder(chunkIndex)
                            .chunkSize(chunkData.length)
                            .checksum(chunkChecksum)
                            .isReplica(!isPrimary)
                            .storagePath(result.getStoragePath())
                            .build();

                    allChunks.add(chunk);

                    // Update used capacity on the node
                    node.setUsedCapacity(node.getUsedCapacity() + chunkData.length);
                    storageNodeRepository.save(node);
                }

                log.debug("Chunk {}/{} distributed to {} nodes",
                        chunkIndex + 1, totalChunks, targetNodes.size());
            }

            fileChunkRepository.saveAll(allChunks);

            // Mark upload complete
            metadata.setUploadStatus(FileMetadata.UploadStatus.COMPLETE);
            metadata.setChunks(allChunks);

            // Update owner storage usage
            owner.setStorageUsed(owner.getStorageUsed() + content.length);

            return fileMetadataRepository.save(metadata);

        } catch (Exception e) {
            metadata.setUploadStatus(FileMetadata.UploadStatus.FAILED);
            fileMetadataRepository.save(metadata);
            log.error("Upload failed for file {}: {}", storedName, e.getMessage(), e);
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Reassembles the full file from its chunks, using failover when a primary
     * node is unavailable.
     *
     * @return raw file bytes in correct order
     */
    public byte[] downloadFile(FileMetadata metadata) throws IOException {
        List<FileChunk> allChunks = fileChunkRepository
                .findByFileOrderByChunkOrder(metadata);

        // Group by chunk_order so we can find replicas quickly
        Map<Integer, List<FileChunk>> chunksByOrder = allChunks.stream()
                .collect(Collectors.groupingBy(FileChunk::getChunkOrder));

        ByteArrayOutputStream assembledFile = new ByteArrayOutputStream();
        Set<String> failedNodes = new HashSet<>();

        for (int i = 0; i < metadata.getTotalChunks(); i++) {
            List<FileChunk> copies = chunksByOrder.get(i);
            if (copies == null || copies.isEmpty()) {
                throw new IOException("Missing chunk at index " + i);
            }

            byte[] chunkData = fetchChunkWithFailover(copies, failedNodes);
            assembledFile.write(chunkData);
        }

        byte[] fullFile = assembledFile.toByteArray();

        // Integrity check
        String actualChecksum = sha256Hex(fullFile);
        if (!actualChecksum.equals(metadata.getChecksum())) {
            throw new IOException("File integrity check failed: checksum mismatch");
        }

        return fullFile;
    }

    // ── Soft delete & restore ─────────────────────────────────────────────────

    @Transactional
    public void softDeleteFile(FileMetadata file) {
        file.setDeletedAt(LocalDateTime.now());
        fileMetadataRepository.save(file);
        log.info("Soft-deleted file {} (recoverable for 30 days)", file.getId());
    }

    @Transactional
    public FileMetadata restoreFile(FileMetadata file) {
        if (!file.isRecoverable()) {
            throw new IllegalStateException(
                "File cannot be restored: 30-day retention window has expired");
        }
        file.setDeletedAt(null);
        FileMetadata restored = fileMetadataRepository.save(file);
        log.info("Restored file {}", file.getId());
        return restored;
    }

    /**
     * Permanently removes a file and all its chunk records from nodes + DB.
     * Called by the scheduler after the 30-day window, or immediately on
     * forced hard-delete.
     */
    @Transactional
    public void hardDeleteFile(FileMetadata file) {
        List<FileChunk> chunks = fileChunkRepository.findByFileOrderByChunkOrder(file);

        // Best-effort: delete from each node (ignore errors)
        chunks.forEach(chunk -> {
            try {
                deleteChunkFromNode(chunk);
            } catch (Exception e) {
                log.warn("Could not delete chunk {} from node {}: {}",
                        chunk.getId(), chunk.getNodeId(), e.getMessage());
            }
        });

        fileMetadataRepository.delete(file);
        log.info("Hard-deleted file {}", file.getId());
    }

    // ── Chunk-level I/O ───────────────────────────────────────────────────────

    /**
     * Tries each copy of a chunk (primary first, then replicas) until one succeeds.
     */
    private byte[] fetchChunkWithFailover(
            List<FileChunk> copies, Set<String> failedNodes) throws IOException {

        // Primary first, then replicas
        List<FileChunk> ordered = copies.stream()
                .sorted(Comparator.comparing(FileChunk::isReplica))
                .collect(Collectors.toList());

        for (FileChunk copy : ordered) {
            if (failedNodes.contains(copy.getNodeId())) continue;

            try {
                return downloadChunkFromNode(copy);
            } catch (Exception e) {
                log.warn("Failed to fetch chunk {} from node {}. Trying next replica. Error: {}",
                        copy.getChunkOrder(), copy.getNodeId(), e.getMessage());
                failedNodes.add(copy.getNodeId());
            }
        }

        throw new IOException("All replicas failed for chunk order " +
                ordered.get(0).getChunkOrder());
    }

    private ChunkUploadResult uploadChunkToNode(
            StorageNode node, String fileId, int chunkIndex, byte[] chunkData) {

        String url = node.getBaseUrl() + "/api/chunks/upload";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("fileId", fileId);
        body.add("chunkIndex", String.valueOf(chunkIndex));
        body.add("chunk", new ByteArrayResource(chunkData) {
            @Override public String getFilename() {
                return fileId + "_" + chunkIndex;
            }
        });

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<ChunkUploadResult> response = restTemplate.postForEntity(
                url, request, ChunkUploadResult.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Node " + node.getId() + " returned " +
                    response.getStatusCode() + " for chunk upload");
        }

        return response.getBody();
    }

    private byte[] downloadChunkFromNode(FileChunk chunk) {
        StorageNode node = storageNodeRepository.findById(chunk.getNodeId())
                .orElseThrow(() -> new RuntimeException("Node not found: " + chunk.getNodeId()));

        if (!node.isAvailable()) {
            throw new RuntimeException("Node " + chunk.getNodeId() + " is not available");
        }

        String url = node.getBaseUrl() + "/api/chunks/" + chunk.getId();
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to download chunk from node " + chunk.getNodeId());
        }

        // Verify chunk integrity
        String actualChecksum = sha256Hex(response.getBody());
        if (!actualChecksum.equals(chunk.getChecksum())) {
            throw new RuntimeException("Chunk integrity check failed on node " + chunk.getNodeId());
        }

        return response.getBody();
    }

    private void deleteChunkFromNode(FileChunk chunk) {
        StorageNode node = storageNodeRepository.findById(chunk.getNodeId())
                .orElseThrow(() -> new RuntimeException("Node not found: " + chunk.getNodeId()));
        String url = node.getBaseUrl() + "/api/chunks/" + chunk.getId();
        restTemplate.delete(url);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
