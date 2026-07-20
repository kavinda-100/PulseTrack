package com.kavinda.auth_service.security;

import com.kavinda.auth_service.entity.OAuthProvider;

public record ExternalIdentity(
        OAuthProvider provider,
        String providerSubject,
        String email,
        boolean emailVerified,
        String username,
        String displayName,
        String avatarUrl
) {
}