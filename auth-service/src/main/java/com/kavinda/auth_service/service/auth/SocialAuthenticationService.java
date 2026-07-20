package com.kavinda.auth_service.service.auth;

import com.kavinda.auth_service.entity.*;
import com.kavinda.auth_service.exceptions.types.ResourceConflictException;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.repository.IOAuthAccountRepository;
import com.kavinda.auth_service.repository.IRoleRepository;
import com.kavinda.auth_service.security.ExternalIdentity;
import com.kavinda.auth_service.utils.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialAuthenticationService {

    private final IAppUserRepository appUserRepository;
    private final IOAuthAccountRepository oauthAccountRepository;
    private final IRoleRepository roleRepository;
    private final EmailNormalizer emailNormalizer;

    @Value("${app.authorization.bootstrap-super-admin-email:}")
    private String bootstrapSuperAdminEmail;

    @Transactional
    public AppUser authenticate(ExternalIdentity identity) {
        validateIdentity(identity);

        String normalizedEmail = emailNormalizer.normalize(identity.email());

        /*
         * Provider identity always has priority over email matching.
         */
        OAuthAccount existingProviderAccount = oauthAccountRepository
                .findWithUserAuthoritiesByProviderAndProviderSubject(
                        identity.provider(),
                        identity.providerSubject()
                )
                .orElse(null);

        if (existingProviderAccount != null) {
            return authenticateExistingProvider(
                    existingProviderAccount,
                    identity
            );
        }

        /*
         * This is a new provider account.
         *
         * A matching verified email attaches it to an existing AppUser.
         * Otherwise, a new AppUser is created.
         */
        AppUser user = appUserRepository
                .findByNormalizedEmail(normalizedEmail)
                .orElseGet(() ->
                        createUser(identity, normalizedEmail)
                );

        ensureUserCanAuthenticate(user);
        ensureProviderCanBeLinked(user, identity.provider());

        if (user.getRoles().isEmpty()) {
            assignInitialRoles(user, normalizedEmail);
        }

        user.setLastLoginAt(Instant.now());

        AppUser savedUser = appUserRepository.save(user);

        OAuthAccount providerAccount = createProviderAccount(savedUser, identity);

        oauthAccountRepository.save(providerAccount);

        return loadWithAuthorities(savedUser.getId());
    }

    private AppUser authenticateExistingProvider(OAuthAccount account, ExternalIdentity identity) {
        AppUser user = account.getUser();

        ensureUserCanAuthenticate(user);

        /*
         * Provider metadata can safely change over time.
         */
        account.setProviderEmail(identity.email());
        account.setProviderEmailVerified(
                identity.emailVerified()
        );
        account.setProviderUsername(identity.username());
        account.setProviderDisplayName(
                identity.displayName()
        );
        account.setProviderAvatarUrl(identity.avatarUrl());
        account.setLastLoginAt(Instant.now());

        /*
         * Do not replace AppUser.email, displayName or avatarUrl here.
         * Those are the canonical PulseTrack profile fields.
         */
        user.setLastLoginAt(Instant.now());

        appUserRepository.save(user);
        oauthAccountRepository.save(account);

        return loadWithAuthorities(user.getId());
    }

    private AppUser createUser(ExternalIdentity identity, String normalizedEmail) {
        AppUser user = AppUser.builder()
                .email(identity.email().trim())
                .normalizedEmail(normalizedEmail)
                .displayName(resolveDisplayName(identity))
                .avatarUrl(identity.avatarUrl())
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .lastLoginAt(Instant.now())
                .roles(new HashSet<>())
                .build();

        assignInitialRoles(user, normalizedEmail);

        return user;
    }

    private OAuthAccount createProviderAccount(AppUser user, ExternalIdentity identity) {
        return OAuthAccount.builder()
                .user(user)
                .provider(identity.provider())
                .providerSubject(identity.providerSubject())
                .providerEmail(identity.email().trim())
                .providerEmailVerified(
                        identity.emailVerified()
                )
                .providerUsername(identity.username())
                .providerDisplayName(identity.displayName())
                .providerAvatarUrl(identity.avatarUrl())
                .lastLoginAt(Instant.now())
                .build();
    }

    private void assignInitialRoles(AppUser user, String normalizedEmail) {
        Role userRole = findRole(RoleName.USER);
        user.getRoles().add(userRole);

        if (isBootstrapSuperAdmin(normalizedEmail)) {
            Role superAdminRole = findRole(RoleName.SUPER_ADMIN);

            user.getRoles().add(superAdminRole);
        }
    }

    private void ensureProviderCanBeLinked(AppUser user, OAuthProvider provider) {
        oauthAccountRepository
                .findByUserIdAndProvider(
                        user.getId(),
                        provider
                )
                .ifPresent(existing -> {
                    throw new ResourceConflictException(
                            "This user already has a "
                                    + provider
                                    + " account linked"
                    );
                });
    }

    private void validateIdentity(ExternalIdentity identity) {
        if (identity == null) {
            throw oauthFailure(
                    "missing_identity",
                    "External identity is missing"
            );
        }

        if (identity.provider() == null) {
            throw oauthFailure(
                    "missing_provider",
                    "Authentication provider is missing"
            );
        }

        if (isBlank(identity.providerSubject())) {
            throw oauthFailure(
                    "missing_subject",
                    "Provider subject is missing"
            );
        }

        if (isBlank(identity.email())) {
            throw oauthFailure(
                    "missing_email",
                    "Provider email is missing"
            );
        }

        if (!identity.emailVerified()) {
            throw oauthFailure(
                    "email_not_verified",
                    "Provider email is not verified"
            );
        }
    }

    private void ensureUserCanAuthenticate(AppUser user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw oauthFailure(
                    "account_suspended",
                    "This user account has been suspended"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw oauthFailure(
                    "account_inactive",
                    "This user account is not active"
            );
        }
    }

    private AppUser loadWithAuthorities(UUID userId) {
        return appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user was not found"
                        )
                );
    }

    private Role findRole(RoleName roleName) {
        return roleRepository
                .findByName(roleName)
                .orElseThrow(() ->
                        new IllegalStateException(
                                roleName
                                        + " role is not initialized"
                        )
                );
    }

    private boolean isBootstrapSuperAdmin(String normalizedEmail) {
        if (bootstrapSuperAdminEmail == null || bootstrapSuperAdminEmail.isBlank()) {
            return false;
        }

        return emailNormalizer
                .normalize(bootstrapSuperAdminEmail)
                .equals(normalizedEmail);
    }

    private String resolveDisplayName(ExternalIdentity identity) {
        if (!isBlank(identity.displayName())) {
            return identity.displayName().trim();
        }

        if (!isBlank(identity.username())) {
            return identity.username().trim();
        }

        return identity.email().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private OAuth2AuthenticationException oauthFailure(
            String code,
            String message
    ) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(code),
                message
        );
    }
}