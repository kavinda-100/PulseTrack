package com.kavinda.auth_service.dtos;

import com.kavinda.auth_service.entity.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
        @NotBlank(message = "user role is required")
        @NotNull(message = "user role cannot be null")
        RoleName role
) {
}
