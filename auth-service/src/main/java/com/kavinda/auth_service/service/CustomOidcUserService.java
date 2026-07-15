package com.kavinda.auth_service.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService oidcUserService;

    public CustomOidcUserService() {
        this.oidcUserService = new OidcUserService();
    }

    @Override
    public @Nullable OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("Loading user for registrationId: {}", registrationId);

        OidcUser oidcUser = this.oidcUserService.loadUser(userRequest);
        log.info("OidcUser loaded: {}", oidcUser);

        String email = oidcUser.getEmail();
        String name = oidcUser.getName();
        log.info("User loaded: email={}, name={}", email, name);

        return oidcUser;
    }
}
