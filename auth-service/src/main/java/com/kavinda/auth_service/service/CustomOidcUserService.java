package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.GoogleUserProfile;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final GoogleAccountProvisioningService googleAccountProvisioningService;
    private final OidcUserService oidcUserService;

    public CustomOidcUserService(GoogleAccountProvisioningService userProvisioningService) {
        this.googleAccountProvisioningService = userProvisioningService;
        this.oidcUserService = new OidcUserService();
    }

    @Override
    public @Nullable OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("Loading user for registrationId: {}", registrationId);

        OidcUser oidcUser = this.oidcUserService.loadUser(userRequest);
        log.info("OidcUser loaded: {}", oidcUser);

        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String displayName = oidcUser.getFullName();
        String picture = oidcUser.getPicture();
        Boolean emailVerifiedClaim = oidcUser.getEmailVerified();

        boolean emailVerified =
                Boolean.TRUE.equals(emailVerifiedClaim);

        checkClaims(oidcUser, registrationId);

        switch (registrationId) {
            case "google" -> {
                GoogleUserProfile profile = new GoogleUserProfile(
                        subject,
                        email,
                        displayName != null && !displayName.isBlank()
                                ? displayName
                                : email,
                        picture,
                        emailVerified
                );

                var localUser = googleAccountProvisioningService.provisionGoogleUser(profile);

                log.info(
                        "Google user provisioned: userId={}, provider=GOOGLE",
                        localUser.getId()
                );
            }
            case "microsoft" -> {
                // Handle Microsoft user provisioning here
                log.info("Microsoft user provisioning is not implemented yet.");
            }
            default -> throw oauthFailure(
                    "unsupported_provider",
                    "Unsupported OAuth2 provider: " + registrationId
            );
        }

        return oidcUser;
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

    private void checkClaims(OidcUser oidcUser, String registrationId) {
        if (oidcUser.getSubject() == null || oidcUser.getSubject().isBlank()) {
            throw oauthFailure(
                    "missing_subject",
                    registrationId + " did not provide a valid subject identifier"
            );
        }

        if (oidcUser.getEmail() == null || oidcUser.getEmail().isBlank()) {
            throw oauthFailure(
                    "missing_email",
                    registrationId + " did not provide an email address"
            );
        }

        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw oauthFailure(
                    "email_not_verified",
                    registrationId + " email address is not verified"
            );
        }
    }
}
