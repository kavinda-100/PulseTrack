package com.kavinda.auth_service.security;

import com.kavinda.auth_service.entity.OAuthProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AppOidcPrincipal implements OidcUser, AppPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final String displayName;
    private final OAuthProvider provider;

    private final Map<String, Object> claims;
    private final Collection<? extends GrantedAuthority> authorities;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public AppOidcPrincipal(
            UUID userId,
            String email,
            String displayName,
            OAuthProvider provider,
            Map<String, Object> claims,
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            OidcUserInfo userInfo
    ) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.provider = provider;
        this.claims = Map.copyOf(claims);
        this.authorities = List.copyOf(authorities);
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public UUID userId() {
        return userId;
    }

    @Override
    public String email() {
        return email;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public OAuthProvider provider() {
        return provider;
    }

    /**
     * The Spring Security authentication name.
     * <p>
     * Returning the local user ID makes Authentication#getName()
     * provider-neutral as well.
     */
    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public Map<String, Object> getClaims() {
        return claims;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return claims;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }
}
