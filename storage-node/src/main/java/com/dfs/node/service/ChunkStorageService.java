package com.dfs.node.service;

import com.dfs.node.config.NodeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * ChunkStorageService — handles raw byte I/O for chunk files.
 *
 * Storage layout on disk:
 *   {storageRoot}/{fileId}/{chunkIndex}.chunk
 *
 * e.g. /data/dfs/node-1/3f8a1c.../0.chunk
 *                                  1.chunk
 *                                  2.chunk
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkStorageService {

    private final NodeProperties nodeProperties;

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persists a chunk to disk and returns the absolute storage path.
     * Creates intermediate directories as needed.
     */
    public String storeChunk(String fileId, int chunkIndex, MultipartFile chunk)
            throws IOException {

        Path dir  = resolveDir(fileId);
        Files.createDirectories(dir);

        Path filePath = dir.resolve(chunkIndex + ".chunk");
        chunk.transferTo(filePath.toFile());

        log.debug("Stored chunk: {}", filePath);
        return filePath.toAbsolutePath().toString();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Reads a chunk from disk given its absolute storage path (as recorded by
     * the gateway in file_chunks.storage_path).
     */
    public byte[] readChunk(String storagePath) throws IOException {
        Path path = Path.of(storagePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Chunk not found: " + storagePath);
        }
        return Files.readAllBytes(path);
    }

    /**
     * Convenience overload: read by fileId + chunkIndex.
     */
    public byte[] readChunk(String fileId, int chunkIndex) throws IOException {
        Path filePath = resolveDir(fileId).resolve(chunkIndex + ".chunk");
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException(
                "Chunk not found: fileId=" + fileId + " index=" + chunkIndex);
        }
        return Files.readAllBytes(filePath);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public boolean deleteChunk(String storagePath) {
        try {
            return Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException e) {
            log.warn("Could not delete chunk at {}: {}", storagePath, e.getMessage());
            return false;
        }
    }

    /**
     * Delete all chunks for a fileId (entire directory).
     */
    public void deleteAllChunks(String fileId) throws IOException {
        Path dir = resolveDir(fileId);
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.compareTo(a))  // files before dirs
                      .forEach(p -> {
                          try { Files.delete(p); }
                          catch (IOException e) {
                              log.warn("Failed to delete {}: {}", p, e.getMessage());
                          }
                      });
            }
            log.info("Deleted all chunks for file {}", fileId);
        }
    }

    // ── Integrity ─────────────────────────────────────────────────────────────

    /**
     * Computes SHA-256 of a stored chunk and compares it with the provided checksum.
     */
    public boolean verifyChecksum(String storagePath, String expectedChecksum) {
        try {
            byte[] data = readChunk(storagePath);
            String actual = sha256Hex(data);
            return actual.equals(expectedChecksum);
        } catch (IOException e) {
            log.error("Checksum verification failed for {}: {}", storagePath, e.getMessage());
            return false;
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("nodeId", nodeProperties.getId());
        stats.put("zone",   nodeProperties.getZone());
        stats.put("totalCapacity", nodeProperties.getTotalCapacity());

        try {
            Path root = Path.of(nodeProperties.getStorageRoot());
            Files.createDirectories(root);
            long used = Files.walk(root)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); }
                        catch (IOException e) { return 0L; }
                    })
                    .sum();
            stats.put("usedCapacity", used);
            stats.put("freeCapacity", nodeProperties.getTotalCapacity() - used);
        } catch (IOException e) {
            stats.put("usedCapacity", -1L);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Path resolveDir(String fileId) {
        return Path.of(nodeProperties.getStorageRoot(), fileId);
    }

    private static String sha256Hex(byte[] data) {
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
