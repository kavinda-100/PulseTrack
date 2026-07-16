package com.kavinda.auth_service.controllers;

import com.kavinda.auth_service.dtos.UserStatusUpdateResponse;
import com.kavinda.auth_service.security.AppPrincipal;
import com.kavinda.auth_service.service.UserAdministrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdministrationController {

    private final UserAdministrationService userAdministrationService;

    @PatchMapping("/{userId}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<UserStatusUpdateResponse> suspendUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AppPrincipal principal
    ) {
        var response = userAdministrationService.suspendUser(
                userId,
                principal
        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<UserStatusUpdateResponse> activateUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AppPrincipal principal
    ) {
        var response = userAdministrationService.activateUser(
                userId,
                principal
        );

        return ResponseEntity.ok(response);
    }
}
