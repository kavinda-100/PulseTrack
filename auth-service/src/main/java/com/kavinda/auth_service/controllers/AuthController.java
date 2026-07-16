package com.kavinda.auth_service.controllers;

import com.kavinda.auth_service.dtos.UserProfileResponse;
import com.kavinda.auth_service.security.AppPrincipal;
import com.kavinda.auth_service.service.GoogleAccountProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAccountProvisioningService googleAccountProvisioningService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal AppPrincipal principal) {
        UserProfileResponse response = googleAccountProvisioningService.findById(principal.userId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal == null) {
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
