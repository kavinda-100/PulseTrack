package com.kavinda.auth_service.service;

import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.Role;
import com.kavinda.auth_service.entity.RoleName;
import com.kavinda.auth_service.exceptions.types.ForbiddenOperationException;
import com.kavinda.auth_service.exceptions.types.ResourceConflictException;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.repository.IRoleRepository;
import com.kavinda.auth_service.security.AppPrincipal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final IAppUserRepository appUserRepository;
    private final IRoleRepository roleRepository;

    @Transactional
    public void assignRole(
            UUID targetUserId,
            RoleName roleName,
            AppPrincipal currentPrincipal
    ) {
        verifyRoleManagementPermission(
                roleName,
                currentPrincipal
        );

        AppUser targetUser = findUser(targetUserId);
        Role role = findRole(roleName);

        boolean added = targetUser.getRoles().add(role);

        if (!added) {
            throw new ResourceConflictException(
                    "User already has role: " + roleName
            );
        }
    }

    @Transactional
    public void removeRole(
            UUID targetUserId,
            RoleName roleName,
            AppPrincipal currentPrincipal
    ) {
        verifyRoleManagementPermission(
                roleName,
                currentPrincipal
        );

        AppUser targetUser = findUser(targetUserId);

        if (targetUserId.equals(currentPrincipal.userId()) && roleName == RoleName.SUPER_ADMIN) {
            throw new ForbiddenOperationException(
                    "You cannot remove your own SUPER_ADMIN role"
            );
        }

        boolean removed = targetUser.getRoles()
                .removeIf(role ->
                        role.getName() == roleName
                );

        if (!removed) {
            throw new ResourceNotFoundException(
                    "User does not have role: " + roleName
            );
        }

        if (targetUser.getRoles().isEmpty()) {
            throw new ForbiddenOperationException(
                    "A user must have at least one role"
            );
        }
    }

    private void verifyRoleManagementPermission(
            RoleName roleName,
            AppPrincipal principal
    ) {
        boolean superAdmin = principal.hasAuthority("ROLE_SUPER_ADMIN");

        if ((roleName == RoleName.ADMIN || roleName == RoleName.SUPER_ADMIN) && !superAdmin) {
            throw new ForbiddenOperationException(
                    "Only a super administrator can manage "
                            + roleName + " roles"
            );
        }
    }

    private AppUser findUser(UUID userId) {
        return appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );
    }

    private Role findRole(RoleName roleName) {
        return roleRepository
                .findByName(roleName)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Role not found: " + roleName
                        )
                );
    }
}
