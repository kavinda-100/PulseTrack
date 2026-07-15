package com.kavinda.auth_service.controllers;

import com.kavinda.auth_service.dtos.UserProfileResponse;
import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.service.GoogleAccountProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAccountProvisioningService googleAccountProvisioningService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = googleAccountProvisioningService
                .findGoogleUser(oidcUser.getSubject());

        UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.isEmailVerified())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        if (oidcUser == null) {
            return ResponseEntity.status(401).body(
                    java.util.Map.of(
                            "status", "unauthenticated"
                    )
            );
        }

        return ResponseEntity.ok(
                java.util.Map.of(
                        "status", "authenticated"
                )
        );
    }
}
