package com.kavinda.auth_service.dtos;

import com.kavinda.auth_service.entity.UserStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UserStatusUpdateResponse(
        UUID userId,
        UserStatus status,
        int revokedSessions
) {
}
