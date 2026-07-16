package com.kavinda.auth_service.dtos;

import com.kavinda.auth_service.entity.UserStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        boolean emailVerified,
        UserStatus status,
        Set<String> roles,
        Set<String> permissions,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}
