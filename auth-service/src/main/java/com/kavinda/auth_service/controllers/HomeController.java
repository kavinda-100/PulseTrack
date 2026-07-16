package com.kavinda.auth_service.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
                "LogOut URL", "/logout",
                "User Info URL", "/api/v1/auth/me",
                "Auth Status URL", "/api/v1/auth/status"
        ));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOperation() {
        return "Admin operation executed successfully.";
    }

    @GetMapping("/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminOperation() {
        return "Super Admin operation executed successfully.";
    }
}
