package com.kavinda.auth_service.controllers;

import com.kavinda.auth_service.dtos.AssignRoleRequest;
import com.kavinda.auth_service.entity.RoleName;
import com.kavinda.auth_service.security.AppPrincipal;
import com.kavinda.auth_service.service.UserRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService userRoleService;

    @PostMapping("/{userId}/assign-roles")
    @PreAuthorize("hasAuthority('role:assign')")
    public ResponseEntity<Map<String, Object>> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal AppPrincipal principal
    ) {
        userRoleService.assignRole(
                userId,
                request.role(),
                principal
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Role assigned successfully"
        ));
    }

    @DeleteMapping("/{userId}/remove-roles/{roleName}")
    @PreAuthorize("hasAuthority('role:remove')")
    public ResponseEntity<Map<String, Object>> removeRole(
            @PathVariable UUID userId,
            @PathVariable RoleName roleName,
            @AuthenticationPrincipal AppPrincipal principal
    ) {
        userRoleService.removeRole(
                userId,
                roleName,
                principal
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Role removed successfully"
        ));
    }
}
