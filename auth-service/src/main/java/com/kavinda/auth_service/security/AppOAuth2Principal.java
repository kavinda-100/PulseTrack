package com.kavinda.auth_service.security;

import com.kavinda.auth_service.entity.OAuthProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AppOAuth2Principal implements OAuth2User, AppPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final String displayName;
    private final OAuthProvider provider;

    private final Map<String, Object> attributes;
    private final List<GrantedAuthority> authorities;

    public AppOAuth2Principal(
            UUID userId,
            String email,
            String displayName,
            OAuthProvider provider,
            Map<String, Object> attributes,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.provider = provider;
        this.attributes = Map.copyOf(attributes);
        this.authorities = List.copyOf(authorities);
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
     * This must remain the local AppUser UUID.
     * <p>
     * Redis session indexing and session revocation rely on it.
     */
    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}