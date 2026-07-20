package com.kavinda.auth_service.controllers;

import com.kavinda.auth_service.dtos.UserProfileResponse;
import com.kavinda.auth_service.security.AppPrincipal;
import com.kavinda.auth_service.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('profile:read')")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal AppPrincipal principal) {
        UserProfileResponse response = userProfileService.findById(principal.userId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> status(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal == null) {
            return ResponseEntity
                    .status(401)
                    .body(Map.of(
                            "status",
                            "unauthenticated"
                    ));
        }

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "authenticated"
                )
        );
    }
}
