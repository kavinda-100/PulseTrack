package com.kavinda.auth_service.controllers;

import com.kavinda.auth_service.security.AppPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the Auth Service",
                "status", "success",
                "timestamp", Instant.now().toString(),
                "version", "1.0.0",
                "Google OAuth2 Login URL", "/oauth2/authorization/google",
                "GitHub OAuth2 Login URL", "/oauth2/authorization/github",
                "LogOut URL", "/logout",
                "User Info URL", "/api/v1/auth/me",
                "Auth Status URL", "/api/v1/auth/status"
        ));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminOperation() {
        return ResponseEntity.ok(Map.of(
                "message", "Admin operation executed successfully."
        ));
    }

    @GetMapping("/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> superAdminOperation() {
        return ResponseEntity.ok(Map.of(
                "message", "Super Admin operation executed successfully."
        ));
    }

    @GetMapping("/debug/principal")
    public Map<String, Object> principal(Authentication authentication) {
        AppPrincipal principal = (AppPrincipal) authentication.getPrincipal();

        return Map.of(
                "authenticationName", authentication.getName(),
                "principalType", principal.getClass().getName(),
                "userId", principal.userId(),
                "provider", principal.provider(),
                "authorities", principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .sorted()
                        .toList()
        );
    }
}
