package com.kavinda.auth_service.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubEmailResponse(
        String email,
        boolean primary,
        boolean verified,
        String visibility
) {
}
