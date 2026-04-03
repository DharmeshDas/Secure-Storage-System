package com.dfs.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadResult {
    private String  chunkId;
    private String  storagePath;
    private boolean success;
    private String  errorMessage;
}
