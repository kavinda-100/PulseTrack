package com.kavinda.auth_service.security;

import com.kavinda.auth_service.entity.OAuthProvider;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public interface AppPrincipal extends Serializable {

    UUID userId();

    String email();

    String displayName();

    OAuthProvider provider();

    Collection<? extends GrantedAuthority> getAuthorities();

    default boolean hasAuthority(String authority) {
        return getAuthorities().stream()
                .anyMatch(grantedAuthority ->
                        Objects.equals(grantedAuthority.getAuthority(), authority)
                );
    }
}
