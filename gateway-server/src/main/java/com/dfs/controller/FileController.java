package com.dfs.controller;

import com.dfs.model.FileMetadata;
import com.dfs.model.User;
import com.dfs.repository.FileMetadataRepository;
import com.dfs.service.FileChunkingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileChunkingService chunkingService;
    private final FileMetadataRepository fileRepo;

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping("/upload")
    public ResponseEntity<FileMetadata> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {

        FileMetadata saved = chunkingService.uploadFile(file, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<FileMetadata>> listFiles(
            @AuthenticationPrincipal User user) {

        List<FileMetadata> files = fileRepo
                .findByOwnerAndDeletedAtIsNull(user);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/trash")
    public ResponseEntity<List<FileMetadata>> listTrash(
            @AuthenticationPrincipal User user) {

        List<FileMetadata> files = fileRepo
                .findByOwnerAndDeletedAtIsNotNull(user);
        return ResponseEntity.ok(files);
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) throws IOException {

        FileMetadata meta = getOwnedFile(id, user);
        byte[] data = chunkingService.downloadFile(meta);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(meta.getMimeType()))
                .contentLength(data.length)
                .body(data);
    }

    // ── Soft-delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        FileMetadata meta = getOwnedFile(id, user);
        chunkingService.softDeleteFile(meta);
        return ResponseEntity.noContent().build();
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/restore")
    public ResponseEntity<FileMetadata> restore(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        FileMetadata meta = getDeletedFile(id, user);
        return ResponseEntity.ok(chunkingService.restoreFile(meta));
    }

    // ── Permanent delete ──────────────────────────────────────────────────────

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        FileMetadata meta = getDeletedFile(id, user);
        chunkingService.hardDeleteFile(meta);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FileMetadata getOwnedFile(Long id, User user) {
        return fileRepo.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "File not found"));
    }

    private FileMetadata getDeletedFile(Long id, User user) {
        return fileRepo.findByIdAndOwnerAndDeletedAtIsNotNull(id, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "File not found in trash"));
    }
}