package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.UserProfileResponse;
import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final IAppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse findById(UUID userId) {
        AppUser user = appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user was not found"
                        )
                );

        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());

        Set<String> permissions =
                user.getRoles().stream()
                        .flatMap(role ->
                                role.getPermissions().stream()
                        )
                        .map(permission ->
                                permission
                                        .getName()
                                        .authority()
                        )
                        .collect(
                                Collectors.toUnmodifiableSet()
                        );

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.isEmailVerified())
                .status(user.getStatus())
                .roles(roles)
                .permissions(permissions)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}