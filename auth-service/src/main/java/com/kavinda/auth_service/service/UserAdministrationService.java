package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.UserStatusUpdateResponse;
import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.RoleName;
import com.kavinda.auth_service.entity.UserStatus;
import com.kavinda.auth_service.exceptions.types.ForbiddenOperationException;
import com.kavinda.auth_service.exceptions.types.ResourceConflictException;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private final IAppUserRepository appUserRepository;
    private final UserSessionService userSessionService;

    @Transactional
    public UserStatusUpdateResponse suspendUser(
            UUID targetUserId,
            AppPrincipal currentPrincipal
    ) {
        AppUser targetUser = findUserWithRoles(targetUserId);

        validateSuspension(targetUser, currentPrincipal);

        if (targetUser.getStatus() == UserStatus.SUSPENDED) {
            throw new ResourceConflictException(
                    "User is already suspended"
            );
        }

        targetUser.setStatus(UserStatus.SUSPENDED);

        /*
         * Writes the status before revoking sessions.
         *
         * The entity is managed, so dirty checking would persist it at
         * transaction commit anyway. saveAndFlush makes the database update
         * happen before the Redis operation.
         */
        appUserRepository.saveAndFlush(targetUser);

        int revokedSessions = userSessionService.revokeAllSessions(targetUserId);

        return UserStatusUpdateResponse.builder()
                .userId(targetUserId)
                .status(UserStatus.SUSPENDED)
                .revokedSessions(revokedSessions)
                .build();
    }

    @Transactional
    public UserStatusUpdateResponse activateUser(
            UUID targetUserId,
            AppPrincipal currentPrincipal
    ) {
        AppUser targetUser = findUserWithRoles(targetUserId);

        validateTargetManagement(targetUser, currentPrincipal);

        if (targetUser.getStatus() == UserStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "User is already active"
            );
        }

        targetUser.setStatus(UserStatus.ACTIVE);
        appUserRepository.saveAndFlush(targetUser);

        /*
         * Activation does not create or restore a session.
         * The user must authenticate with Google again.
         */
        return UserStatusUpdateResponse.builder()
                .userId(targetUserId)
                .status(UserStatus.ACTIVE)
                .revokedSessions(0)
                .build();
    }

    private AppUser findUserWithRoles(UUID userId) {
        return appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );
    }

    private void validateSuspension(
            AppUser targetUser,
            AppPrincipal currentPrincipal
    ) {
        if (targetUser.getId().equals(currentPrincipal.userId())) {
            throw new ForbiddenOperationException(
                    "You cannot suspend your own account"
            );
        }

        validateTargetManagement(
                targetUser,
                currentPrincipal
        );
    }

    private void validateTargetManagement(
            AppUser targetUser,
            AppPrincipal currentPrincipal
    ) {
        boolean currentUserIsSuperAdmin = currentPrincipal.hasAuthority("ROLE_SUPER_ADMIN");

        boolean targetIsSuperAdmin = targetUser
                .getRoles()
                .stream()
                .anyMatch(role -> role.getName() == RoleName.SUPER_ADMIN);

        /*
         * An ADMIN must not suspend or activate a SUPER_ADMIN.
         */
        if (targetIsSuperAdmin && !currentUserIsSuperAdmin) {
            throw new ForbiddenOperationException(
                    "Only a super administrator can manage "
                            + "another super administrator"
            );
        }
    }
}
