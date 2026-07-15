package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.GoogleUserProfile;
import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.OAuthAccount;
import com.kavinda.auth_service.entity.OAuthProvider;
import com.kavinda.auth_service.entity.UserStatus;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.repository.IOAuthAccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleAccountProvisioningService {

    private final IAppUserRepository appUserRepository;
    private final IOAuthAccountRepository oauthAccountRepository;

    @Transactional
    public AppUser findGoogleUser(String providerSubject) {
        return oauthAccountRepository
                .findByProviderAndProviderSubject(
                        OAuthProvider.GOOGLE,
                        providerSubject
                )
                .map(OAuthAccount::getUser)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user was not found"
                ));
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

            return appUserRepository.save(existingUser);
        }

        // If no existing OAuth account is found, check if a user with the same email exists
        AppUser user = appUserRepository
                .findByEmailIgnoreCase(profile.email())
                .orElseGet(() -> createUser(profile));

        ensureUserCanLogin(user);
        updateUser(user, profile);

        AppUser savedUser = appUserRepository.save(user);

        OAuthAccount account = OAuthAccount.builder()
                .user(savedUser)
                .provider(OAuthProvider.GOOGLE)
                .providerSubject(profile.subject())
                .providerEmail(profile.email())
                .build();

        oauthAccountRepository.save(account);

        return savedUser;
    }

    private AppUser createUser(GoogleUserProfile profile) {
        return AppUser.builder()
                .email(profile.email())
                .displayName(profile.displayName())
                .avatarUrl(profile.avatarUrl())
                .emailVerified(profile.emailVerified())
                .status(UserStatus.ACTIVE)
                .lastLoginAt(Instant.now())
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
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                    "This user account is not active"
            );
        }
    }
}
