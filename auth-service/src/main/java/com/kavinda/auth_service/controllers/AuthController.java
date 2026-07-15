package com.kavinda.auth_service.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(OAuth2AuthenticationToken authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Unauthorized",
                    "status", "error"
            ));
        }

        String email = authentication.getPrincipal().getAttribute("email");
        String name = authentication.getPrincipal().getAttribute("name");

        return ResponseEntity.ok(Map.of(
                "email", email,
                "name", name
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(OAuth2AuthenticationToken authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Unauthorized",
                    "status", "error"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", authentication.isAuthenticated() ? "authenticated" : "not authenticated"
        ));
    }
}
