package com.kavinda.auth_service.service.auth;

import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.OAuthProvider;
import com.kavinda.auth_service.mappers.UserAuthorityMapper;
import com.kavinda.auth_service.security.AppOAuth2Principal;
import com.kavinda.auth_service.security.ExternalIdentity;
import com.kavinda.auth_service.security.GitHubEmailClient;
import com.kavinda.auth_service.security.GitHubEmailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService defaultOAuth2UserService;
    private final GitHubEmailClient gitHubEmailClient;
    private final SocialAuthenticationService socialAuthenticationService;
    private final UserAuthorityMapper userAuthorityMapper;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2User providerUser = defaultOAuth2UserService.loadUser(userRequest);
        log.info(
                "Loaded user from OAuth2 provider: " + "provider={}, attributes={}",
                registrationId,
                providerUser.getAttributes()
        );

        return switch (registrationId) {
            case "github" -> authenticateGitHub(userRequest, providerUser);

            default -> throw oauthFailure(
                    "unsupported_oauth2_provider",
                    "Unsupported OAuth2 provider: "
                            + registrationId
            );
        };
    }

    private AppOAuth2Principal authenticateGitHub(OAuth2UserRequest userRequest, OAuth2User githubUser) {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String providerSubject = getRequiredStringAttribute(githubUser, "id", registrationId);
        String login = getRequiredStringAttribute(githubUser, "login", registrationId);
        String displayName = getOptionalStringAttribute(githubUser, "name");
        String avatarUrl = getOptionalStringAttribute(githubUser, "avatar_url");

        GitHubEmailResponse githubEmail = gitHubEmailClient.getPrimaryVerifiedEmail(userRequest.getAccessToken().getTokenValue());

        ExternalIdentity identity =
                new ExternalIdentity(
                        OAuthProvider.GITHUB,
                        providerSubject,
                        githubEmail.email(),
                        true,
                        login,
                        resolveDisplayName(
                                displayName,
                                login,
                                githubEmail.email()
                        ),
                        avatarUrl
                );

        AppUser appUser = socialAuthenticationService.authenticate(identity);

        Set<GrantedAuthority> authorities = new HashSet<>(githubUser.getAuthorities());

        authorities.addAll(userAuthorityMapper.map(appUser));

        log.info(
                "Social authentication completed: " + "provider={}, userId={}, githubLogin={}, roles={}",
                OAuthProvider.GITHUB,
                appUser.getId(),
                login,
                appUser.getRoles().stream()
                        .map(role ->
                                role.getName().name()
                        )
                        .toList()
        );

        return new AppOAuth2Principal(
                appUser.getId(),
                appUser.getEmail(),
                appUser.getDisplayName(),
                OAuthProvider.GITHUB,
                githubUser.getAttributes(),
                authorities
        );
    }

    private String getRequiredStringAttribute(OAuth2User user, String attributeName, String registrationId) {
        Object value = user.getAttributes().get(attributeName);

        if (value == null) {
            String code = registrationId + "_attribute_missing";
            throw oauthFailure(
                    code,
                    registrationId + " did not provide required attribute: " + attributeName
            );
        }

        String converted = String.valueOf(value);

        if (converted.isBlank()) {
            throw oauthFailure(
                    registrationId + "_attribute_missing",
                    registrationId + " did not provide required attribute: " + attributeName
            );
        }

        return converted;
    }

    private String getOptionalStringAttribute(OAuth2User user, String attributeName) {
        Object value = user.getAttributes().get(attributeName);

        if (value == null) {
            return null;
        }

        String converted = String.valueOf(value);

        return converted.isBlank() ? null : converted;
    }

    private String resolveDisplayName(
            String displayName,
            String login,
            String email
    ) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }

        if (login != null && !login.isBlank()) {
            return login;
        }

        return email;
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