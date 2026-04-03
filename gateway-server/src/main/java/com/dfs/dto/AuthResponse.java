package com.dfs.dto;

import com.dfs.model.User;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String email;
    private String role;
    private Long   storageUsed;
    private Long   storageQuota;
    private String error;

    public static AuthResponse success(String token, User user) {
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .storageUsed(user.getStorageUsed())
                .storageQuota(user.getStorageQuota())
                .build();
    }

    public static AuthResponse error(String message) {
        return AuthResponse.builder().error(message).build();
    }
}
