package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.GoogleUserProfile;
import com.kavinda.auth_service.entity.OAuthProvider;
import com.kavinda.auth_service.mappers.UserAuthorityMapper;
import com.kavinda.auth_service.security.AppOidcPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final GoogleAccountProvisioningService googleAccountProvisioningService;
    private final UserAuthorityMapper userAuthorityMapper;
    private final OidcUserService oidcUserService;

    public CustomOidcUserService(GoogleAccountProvisioningService userProvisioningService, UserAuthorityMapper userAuthorityMapper) {
        this.googleAccountProvisioningService = userProvisioningService;
        this.userAuthorityMapper = userAuthorityMapper;
        this.oidcUserService = new OidcUserService();
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("Loading user for registrationId: {}", registrationId);

        OidcUser oidcUser = this.oidcUserService.loadUser(userRequest);
        log.info("OidcUser loaded: {}", oidcUser);

        return switch (registrationId) {
            case "google" -> provisionGoogleUser(oidcUser, registrationId);
            case "microsoft" -> throw oauthFailure(
                    "provider_not_implemented",
                    "Microsoft authentication is not implemented"
            );
            default -> throw oauthFailure(
                    "unsupported_provider",
                    "Unsupported OAuth2 provider: " + registrationId
            );
        };
    }

    private AppOidcPrincipal provisionGoogleUser(OidcUser oidcUser, String registrationId) {
        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String displayName = resolveDisplayName(oidcUser);
        String picture = oidcUser.getPicture();
        Boolean emailVerifiedClaim = oidcUser.getEmailVerified();

        boolean emailVerified = Boolean.TRUE.equals(emailVerifiedClaim);

        checkClaims(oidcUser, registrationId);

        GoogleUserProfile profile = new GoogleUserProfile(
                subject,
                email,
                displayName,
                picture,
                emailVerified
        );

        var appUser = googleAccountProvisioningService.provisionGoogleUser(profile);

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());

        authorities.addAll(userAuthorityMapper.map(appUser));

        log.info(
                "Google user authenticated: userId={}, roles={}",
                appUser.getId(),
                appUser.getRoles().stream()
                        .map(role -> role.getName().name())
                        .toList()
        );

        return new AppOidcPrincipal(
                appUser.getId(),
                appUser.getEmail(),
                appUser.getDisplayName(),
                OAuthProvider.GOOGLE,
                oidcUser.getClaims(),
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );
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

    private String resolveDisplayName(OidcUser user) {
        String fullName = user.getFullName();

        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }

        return user.getEmail();
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
