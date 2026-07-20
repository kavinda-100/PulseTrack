package com.kavinda.auth_service.service.auth;

import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.OAuthProvider;
import com.kavinda.auth_service.mappers.UserAuthorityMapper;
import com.kavinda.auth_service.security.AppOidcPrincipal;
import com.kavinda.auth_service.security.ExternalIdentity;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService oidcUserService;
    private final SocialAuthenticationService socialAuthenticationService;
    private final UserAuthorityMapper userAuthorityMapper;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OidcUser providerUser = oidcUserService.loadUser(userRequest);

        return switch (registrationId) {
            case "google" -> authenticateGoogle(providerUser);

            default -> throw oauthFailure(
                    "unsupported_oidc_provider",
                    "Unsupported OIDC provider: "
                            + registrationId
            );
        };
    }

    private AppOidcPrincipal authenticateGoogle(OidcUser googleUser) {
        ExternalIdentity identity = new ExternalIdentity(
                OAuthProvider.GOOGLE,
                googleUser.getSubject(),
                googleUser.getEmail(),
                Boolean.TRUE.equals(
                        googleUser.getEmailVerified()
                ),
                googleUser.getPreferredUsername(),
                resolveDisplayName(googleUser),
                googleUser.getPicture()
        );

        AppUser appUser = socialAuthenticationService.authenticate(identity);

        Set<GrantedAuthority> authorities = new HashSet<>(googleUser.getAuthorities());

        authorities.addAll(userAuthorityMapper.map(appUser));

        log.info(
                "Social authentication completed: " + "provider={}, userId={}, roles={}",
                OAuthProvider.GOOGLE,
                appUser.getId(),
                appUser.getRoles().stream()
                        .map(role ->
                                role.getName().name()
                        )
                        .toList()
        );

        return new AppOidcPrincipal(
                appUser.getId(),
                appUser.getEmail(),
                appUser.getDisplayName(),
                OAuthProvider.GOOGLE,
                googleUser.getClaims(),
                authorities,
                googleUser.getIdToken(),
                googleUser.getUserInfo()
        );
    }

    private String resolveDisplayName(OidcUser user) {
        if (!isBlank(user.getFullName())) {
            return user.getFullName();
        }

        if (!isBlank(user.getPreferredUsername())) {
            return user.getPreferredUsername();
        }

        return user.getEmail();
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