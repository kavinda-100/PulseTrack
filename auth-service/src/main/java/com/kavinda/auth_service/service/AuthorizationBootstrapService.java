package com.kavinda.auth_service.service;

import com.kavinda.auth_service.entity.*;
import com.kavinda.auth_service.repository.IPermissionRepository;
import com.kavinda.auth_service.repository.IRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationBootstrapService {

    private final IPermissionRepository permissionRepository;
    private final IRoleRepository roleRepository;

    @Transactional
    public void initialize() {
        createPermissions();

        createOrUpdateRole(
                RoleName.USER,
                "Standard PulseTrack user",
                EnumSet.of(
                        PermissionName.PROFILE_READ,
                        PermissionName.PROFILE_UPDATE,
                        PermissionName.PROJECT_CREATE,
                        PermissionName.PROJECT_READ,
                        PermissionName.PROJECT_UPDATE,
                        PermissionName.PROJECT_DELETE,
                        PermissionName.ANALYTICS_READ
                )
        );

        createOrUpdateRole(
                RoleName.ADMIN,
                "PulseTrack administrator",
                EnumSet.of(
                        PermissionName.PROFILE_READ,
                        PermissionName.PROFILE_UPDATE,
                        PermissionName.PROJECT_CREATE,
                        PermissionName.PROJECT_READ,
                        PermissionName.PROJECT_UPDATE,
                        PermissionName.PROJECT_DELETE,
                        PermissionName.ANALYTICS_READ,

                        PermissionName.USER_READ,
                        PermissionName.USER_UPDATE,
                        PermissionName.USER_DISABLE,

                        PermissionName.ROLE_READ
                )
        );

        createOrUpdateRole(
                RoleName.SUPER_ADMIN,
                "PulseTrack super administrator",
                EnumSet.allOf(PermissionName.class)
        );

        log.info("Roles and permissions initialized successfully");
    }

    private void createPermissions() {
        Arrays.stream(PermissionName.values())
                .forEach(permissionName ->
                        permissionRepository.findByName(permissionName)
                                .orElseGet(() -> permissionRepository.save(
                                                Permission.builder()
                                                        .name(permissionName)
                                                        .description(permissionName.authority())
                                                        .build()
                                        )
                                )
                );
    }

    private void createOrUpdateRole(
            RoleName roleName,
            String description,
            Set<PermissionName> permissionNames
    ) {
        Role role = roleRepository
                .findByName(roleName)
                .orElseGet(() ->
                        Role.builder()
                                .name(roleName)
                                .description(description)
                                .build()
                );

        Set<Permission> permissions = permissionNames.stream()
                .map(permissionName ->
                        permissionRepository
                                .findByName(permissionName)
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "Permission is missing: "
                                                        + permissionName
                                        )
                                )
                )
                .collect(Collectors.toSet());

        role.setDescription(description);
        role.setPermissions(permissions);

        roleRepository.save(role);
    }
}