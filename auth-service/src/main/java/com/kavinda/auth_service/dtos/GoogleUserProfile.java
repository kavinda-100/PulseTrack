package com.kavinda.auth_service.dtos;

public record GoogleUserProfile(
        String subject,
        String email,
        String displayName,
        String avatarUrl,
        boolean emailVerified
) {
}
