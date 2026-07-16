package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.GoogleUserProfile;
import com.kavinda.auth_service.dtos.UserProfileResponse;
import com.kavinda.auth_service.entity.*;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.repository.IOAuthAccountRepository;
import com.kavinda.auth_service.repository.IRoleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoogleAccountProvisioningService {

    private final IAppUserRepository appUserRepository;
    private final IOAuthAccountRepository oauthAccountRepository;
    private final IRoleRepository roleRepository;

    @Value("${app.authorization.bootstrap-super-admin-email}")
    private String bootstrapSuperAdminEmail;

    @Transactional
    public UserProfileResponse findById(UUID userId) {
        AppUser user = appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user was not found"
                ));

        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission ->
                        permission.getName().authority()
                )
                .collect(Collectors.toUnmodifiableSet());

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

    @Transactional
    public AppUser provisionGoogleUser(GoogleUserProfile profile) {
        // Check if an OAuth account already exists for this Google user
        Optional<OAuthAccount> existingAccount = oauthAccountRepository
                .findByProviderAndProviderSubject(
                        OAuthProvider.GOOGLE,
                        profile.subject()
                );

        if (existingAccount.isPresent()) {
            AppUser existingUser = existingAccount.get().getUser();

            ensureUserCanLogin(existingUser);
            updateUser(existingUser, profile);

            existingAccount.get().setProviderEmail(profile.email());

            appUserRepository.save(existingUser);
            oauthAccountRepository.save(existingAccount.get());

            return loadWithAuthorities(existingUser.getId());
        }

        // If no existing OAuth account is found, check if a user with the same email exists
        AppUser user = appUserRepository
                .findByEmailIgnoreCase(profile.email())
                .orElseGet(() -> createUser(profile));

        ensureUserCanLogin(user);
        updateUser(user, profile);

        if (user.getRoles().isEmpty()) {
            user.getRoles().add(resolveInitialRole(profile.email()));
        }

        AppUser savedUser = appUserRepository.save(user);

        OAuthAccount account = OAuthAccount.builder()
                .user(savedUser)
                .provider(OAuthProvider.GOOGLE)
                .providerSubject(profile.subject())
                .providerEmail(profile.email())
                .build();

        oauthAccountRepository.save(account);

        return loadWithAuthorities(savedUser.getId());
    }

    private AppUser createUser(GoogleUserProfile profile) {
        Role initialRole = resolveInitialRole(profile.email());
        Role defaultRole = DefultRole();

        HashSet<Role> roles = new HashSet<>();
        roles.add(initialRole);
        roles.add(defaultRole);

        return AppUser.builder()
                .email(profile.email())
                .displayName(profile.displayName())
                .avatarUrl(profile.avatarUrl())
                .emailVerified(profile.emailVerified())
                .status(UserStatus.ACTIVE)
                .lastLoginAt(Instant.now())
                .roles(roles)
                .build();
    }

    private void updateUser(
            AppUser user,
            GoogleUserProfile profile
    ) {
        user.setEmail(profile.email());
        user.setDisplayName(profile.displayName());
        user.setAvatarUrl(profile.avatarUrl());
        user.setEmailVerified(profile.emailVerified());
        user.setLastLoginAt(Instant.now());
    }

    private void ensureUserCanLogin(AppUser user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_suspended"),
                    "This user account has been suspended"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_inactive"),
                    "This user account is not active"
            );
        }
    }

    private Role resolveInitialRole(String email) {
        RoleName roleName = isBootstrapSuperAdmin(email)
                ? RoleName.SUPER_ADMIN
                : RoleName.ADMIN;

        return roleRepository
                .findByName(roleName)
                .orElseThrow(() ->
                        new IllegalStateException(
                                roleName + " role is not initialized"
                        )
                );
    }

    private Role DefultRole() {
        return roleRepository
                .findByName(RoleName.USER)
                .orElseThrow(() ->
                        new IllegalStateException(
                                RoleName.USER + " role is not initialized"
                        )
                );
    }

    private boolean isBootstrapSuperAdmin(String email) {
        return bootstrapSuperAdminEmail != null
                && !bootstrapSuperAdminEmail.isBlank()
                && bootstrapSuperAdminEmail.equalsIgnoreCase(email);
    }

    private AppUser loadWithAuthorities(UUID userId) {
        return appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User was not found"
                        )
                );
    }
}
