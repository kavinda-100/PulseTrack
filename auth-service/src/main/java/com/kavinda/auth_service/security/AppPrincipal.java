package com.kavinda.auth_service.security;

import com.kavinda.auth_service.entity.OAuthProvider;

import java.io.Serializable;
import java.util.UUID;

public interface AppPrincipal extends Serializable {

    UUID userId();

    String email();

    String displayName();

    OAuthProvider provider();
}
