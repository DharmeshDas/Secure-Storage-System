package com.dfs.node.controller;

import com.dfs.node.service.ChunkStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ChunkController {

    private final ChunkStorageService storageService;

    // ── Upload a chunk ────────────────────────────────────────────────────────

    @PostMapping("/chunks/upload")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("fileId")      String fileId,
            @RequestParam("chunkIndex")  int chunkIndex,
            @RequestParam("chunk")       MultipartFile chunk) throws IOException {

        String storagePath = storageService.storeChunk(fileId, chunkIndex, chunk);

        return ResponseEntity.ok(Map.of(
            "success",     true,
            "storagePath", storagePath,
            "chunkIndex",  chunkIndex,
            "size",        chunk.getSize()
        ));
    }

    // ── Download a chunk by storage path ──────────────────────────────────────

    @GetMapping("/chunks/{chunkId}")
    public ResponseEntity<byte[]> downloadChunk(
            @PathVariable String chunkId,
            @RequestParam("path") String storagePath) throws IOException {

        byte[] data = storageService.readChunk(storagePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(data);
    }

    // ── Delete a chunk ────────────────────────────────────────────────────────

    @DeleteMapping("/chunks/{chunkId}")
    public ResponseEntity<Void> deleteChunk(
            @RequestParam("path") String storagePath) {

        storageService.deleteChunk(storagePath);
        return ResponseEntity.noContent().build();
    }

    // ── Verify checksum ───────────────────────────────────────────────────────

    @GetMapping("/chunks/verify")
    public ResponseEntity<Map<String, Object>> verifyChunk(
            @RequestParam("path")     String storagePath,
            @RequestParam("checksum") String expectedChecksum) {

        boolean valid = storageService.verifyChecksum(storagePath, expectedChecksum);
        return ResponseEntity.ok(Map.of("valid", valid, "path", storagePath));
    }

    // ── Node stats (used by gateway health monitor) ───────────────────────────

    @GetMapping("/node/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(storageService.getStats());
    }
}
