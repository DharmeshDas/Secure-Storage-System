package com.dfs.service;

import com.dfs.config.StorageProperties;
import com.dfs.dto.ChunkUploadResult;
import com.dfs.model.*;
import com.dfs.repository.FileChunkRepository;
import com.dfs.repository.FileMetadataRepository;
import com.dfs.repository.StorageNodeRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileChunkingService {

    private final StorageProperties      storageProperties;
    private final LoadBalancerService    loadBalancerService;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileChunkRepository    fileChunkRepository;
    private final StorageNodeRepository  storageNodeRepository;
    private final RestTemplate           restTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    // ── Internal JWT ──────────────────────────────────────────────────────────
    private String buildInternalToken() {
        return Jwts.builder()
                .subject("gateway-internal")
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private HttpHeaders jwtHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(buildInternalToken());
        return h;
    }

    // ── Upload ────────────────────────────────────────────────────────────────
    @Transactional
    public FileMetadata uploadFile(MultipartFile file, com.dfs.model.User owner)
            throws IOException {

        int    chunkSize   = storageProperties.getChunkSizeBytes();
        int    replication = storageProperties.getReplicationFactor();
        byte[] content     = file.getBytes();
        int    totalChunks = (int) Math.ceil((double) content.length / chunkSize);
        if (totalChunks == 0) totalChunks = 1;

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

            for (int i = 0; i < totalChunks; i++) {
                int    start     = i * chunkSize;
                int    end       = Math.min(start + chunkSize, content.length);
                byte[] chunkData = Arrays.copyOfRange(content, start, end);
                String checksum  = sha256Hex(chunkData);

                List<StorageNode> targets = loadBalancerService
                        .selectNodesForChunk(chunkData.length, replication);

                for (int r = 0; r < targets.size(); r++) {
                    StorageNode       node   = targets.get(r);
                    ChunkUploadResult result = uploadChunkToNode(
                            node, storedName, i, chunkData);

                    allChunks.add(FileChunk.builder()
                            .file(metadata)
                            .nodeId(node.getId())
                            .chunkOrder(i)
                            .chunkSize(chunkData.length)
                            .checksum(checksum)
                            .isReplica(r != 0)
                            .storagePath(result.getStoragePath())
                            .build());

                    node.setUsedCapacity(node.getUsedCapacity() + chunkData.length);
                    storageNodeRepository.save(node);
                }
                log.debug("Chunk {}/{} distributed", i + 1, totalChunks);
            }

            // ── KEY FIX: save chunks first, then update status ────────────────
            // Do NOT call metadata.setChunks() — this causes the orphan deletion
            // error because JPA tries to replace the managed collection.
            // Instead save chunks independently and just update the status.
            fileChunkRepository.saveAll(allChunks);

            // Update status and owner usage — reload fresh to avoid stale state
            metadata = fileMetadataRepository.findById(metadata.getId())
                    .orElseThrow(() -> new RuntimeException("File metadata lost"));
            metadata.setUploadStatus(FileMetadata.UploadStatus.COMPLETE);
            owner.setStorageUsed(owner.getStorageUsed() + content.length);
            return fileMetadataRepository.save(metadata);

        } catch (Exception e) {
            // Reload and mark failed to avoid stale entity issues
            fileMetadataRepository.findById(metadata.getId()).ifPresent(m -> {
                m.setUploadStatus(FileMetadata.UploadStatus.FAILED);
                fileMetadataRepository.save(m);
            });
            log.error("Upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────
    public byte[] downloadFile(FileMetadata metadata) throws IOException {
        List<FileChunk> all = fileChunkRepository
                .findByFileOrderByChunkOrder(metadata);

        Map<Integer, List<FileChunk>> byOrder = all.stream()
                .collect(Collectors.groupingBy(FileChunk::getChunkOrder));

        ByteArrayOutputStream out    = new ByteArrayOutputStream();
        Set<String>           failed = new HashSet<>();

        for (int i = 0; i < metadata.getTotalChunks(); i++) {
            List<FileChunk> copies = byOrder.get(i);
            if (copies == null || copies.isEmpty())
                throw new IOException("Missing chunk " + i);
            out.write(fetchWithFailover(copies, failed));
        }

        byte[] full = out.toByteArray();
        if (!sha256Hex(full).equals(metadata.getChecksum()))
            throw new IOException("File integrity check failed");
        return full;
    }

    // ── Soft delete / restore / hard delete ───────────────────────────────────
    @Transactional
    public void softDeleteFile(FileMetadata f) {
        f.setDeletedAt(LocalDateTime.now());
        fileMetadataRepository.save(f);
    }

    @Transactional
    public FileMetadata restoreFile(FileMetadata f) {
        if (!f.isRecoverable())
            throw new IllegalStateException("30-day window expired");
        f.setDeletedAt(null);
        return fileMetadataRepository.save(f);
    }

    @Transactional
    public void hardDeleteFile(FileMetadata f) {
        fileChunkRepository.findByFileOrderByChunkOrder(f).forEach(c -> {
            try { deleteChunkFromNode(c); }
            catch (Exception e) {
                log.warn("Delete chunk failed: {}", e.getMessage());
            }
        });
        fileMetadataRepository.delete(f);
    }

    // ── Node I/O ──────────────────────────────────────────────────────────────
    private byte[] fetchWithFailover(List<FileChunk> copies,
                                     Set<String> failed) throws IOException {
        copies.sort(Comparator.comparing(FileChunk::isReplica));
        for (FileChunk c : copies) {
            if (failed.contains(c.getNodeId())) continue;
            try { return downloadChunkFromNode(c); }
            catch (Exception e) {
                log.warn("Node {} failed chunk {}: {}",
                        c.getNodeId(), c.getChunkOrder(), e.getMessage());
                failed.add(c.getNodeId());
            }
        }
        throw new IOException("All replicas failed for chunk "
                + copies.get(0).getChunkOrder());
    }

    private ChunkUploadResult uploadChunkToNode(StorageNode node,
                                                String fileId,
                                                int index,
                                                byte[] data) {
        HttpHeaders headers = jwtHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("fileId",     fileId);
        body.add("chunkIndex", String.valueOf(index));
        body.add("chunk", new ByteArrayResource(data) {
            @Override public String getFilename() {
                return fileId + "_" + index;
            }
        });

        log.debug("Uploading chunk {} to node {} url {}",
                index, node.getId(), node.getBaseUrl());

        ResponseEntity<ChunkUploadResult> resp = restTemplate.postForEntity(
                node.getBaseUrl() + "/api/chunks/upload",
                new HttpEntity<>(body, headers),
                ChunkUploadResult.class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null)
            throw new RuntimeException("Node " + node.getId()
                    + " upload failed: " + resp.getStatusCode());

        return resp.getBody();
    }

    private byte[] downloadChunkFromNode(FileChunk chunk) {
        StorageNode node = storageNodeRepository.findById(chunk.getNodeId())
                .orElseThrow(() -> new RuntimeException(
                        "Node not found: " + chunk.getNodeId()));

        if (!node.isAvailable())
            throw new RuntimeException("Node " + chunk.getNodeId() + " unavailable");

        String url = node.getBaseUrl() + "/api/chunks/" + chunk.getId()
                + "?path=" + chunk.getStoragePath();

        ResponseEntity<byte[]> resp = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(jwtHeaders()),
                byte[].class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null)
            throw new RuntimeException("Download failed from " + chunk.getNodeId());

        if (!sha256Hex(resp.getBody()).equals(chunk.getChecksum()))
            throw new RuntimeException("Checksum mismatch on " + chunk.getNodeId());

        return resp.getBody();
    }

    private void deleteChunkFromNode(FileChunk chunk) {
        StorageNode node = storageNodeRepository.findById(chunk.getNodeId())
                .orElseThrow(() -> new RuntimeException(
                        "Node not found: " + chunk.getNodeId()));
        String url = node.getBaseUrl() + "/api/chunks/" + chunk.getId()
                + "?path=" + chunk.getStoragePath();
        restTemplate.exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(jwtHeaders()), Void.class);
    }

    // ── SHA-256 ───────────────────────────────────────────────────────────────
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}