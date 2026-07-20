# Code so-far

## Files in Config Directory

### SecurityConfig.java

```java
package com.kavinda.auth_service.config;

import com.kavinda.auth_service.service.auth.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // cors configuration
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of(frontendUrl));
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                // Disable Basic Auth or form login.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // Disable CSRF protection.
                .csrf(AbstractHttpConfigurer::disable)
                // request matching
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/status").permitAll()
                        .anyRequest().authenticated()
                )
                // oauth2 login configuration
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)
                        )
                        .defaultSuccessUrl(frontendUrl + "/auth/callback", true)
                        .failureUrl(
                                frontendUrl + "/auth/error"
                        )
                )
                // logout configuration
                .logout(logout -> logout
                        .logoutSuccessUrl(frontendUrl)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("SESSION")
                )
                // exception handling configuration
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendError(401, "Unauthorized");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendError(403, "Forbidden");
                        })
                );

        return http.build();
    }
}

```

### RoleHierarchyConfig.java

```java
package com.kavinda.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class RoleHierarchyConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_ADMIN
                ROLE_ADMIN > ROLE_USER
                """);
    }
}

```

### RedisSessionConfig.java

```java
package com.kavinda.auth_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.SaveMode;
import org.springframework.session.FlushMode;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

@Configuration
@EnableRedisIndexedHttpSession(
        redisNamespace = "pulsetrack:auth:sessions",
        flushMode = FlushMode.ON_SAVE,
        saveMode = SaveMode.ON_SET_ATTRIBUTE
)
public class RedisSessionConfig {
}

```

### AuthorizationDataInitializer.java

```java
package com.kavinda.auth_service.config;

import com.kavinda.auth_service.service.auth.AuthorizationBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AuthorizationDataInitializer {

    private final AuthorizationBootstrapService authorizationBootstrapService;

    @Bean
    ApplicationRunner initializeAuthorizationData() {
        return args -> authorizationBootstrapService.initialize();
    }
}
```

## Files in Controller Directory

### AuthController.java

```java
package com.kavinda.auth_service.controllers;

import com.kavinda.auth_service.dtos.UserProfileResponse;
import com.kavinda.auth_service.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAuthority('user:read')")
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

```

### HomeController.java

```java
package com.kavinda.auth_service.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/debug/principal")
    public Map<String, String> principal(Authentication authentication) {
        return Map.of(
                "name", authentication.getName(),
                "type", authentication
                        .getPrincipal()
                        .getClass()
                        .getName()
        );
    }
}

```

### UserRoleController.java

```java
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

```

### UserAdministrationController.java

```java
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

```

## Files in DTOs Directory

### AssignRoleRequest.java

```java
package com.kavinda.auth_service.dtos;

import com.kavinda.auth_service.entity.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
        @NotBlank(message = "user role is required")
        @NotNull(message = "user role cannot be null")
        RoleName role
) {
}

```

### GoogleUserProfile.java

```java
package com.kavinda.auth_service.dtos;

public record GoogleUserProfile(
        String subject,
        String email,
        String displayName,
        String avatarUrl,
        boolean emailVerified
) {
}

```

### UserProfileResponse.java

```java
package com.kavinda.auth_service.dtos;

import com.kavinda.auth_service.entity.UserStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        boolean emailVerified,
        UserStatus status,
        Set<String> roles,
        Set<String> permissions,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}

```

### UserStatusUpdateResponse.java

```java
package com.kavinda.auth_service.dtos;

import com.kavinda.auth_service.entity.UserStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UserStatusUpdateResponse(
        UUID userId,
        UserStatus status,
        int revokedSessions
) {
}

```

## Files in Entity Directory

### AppUser.java

```java
package com.kavinda.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "app_users",
        indexes = {
                @Index(
                        name = "idx_app_users_email",
                        columnList = "email"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(
                    name = "user_id",
                    nullable = false,
                    foreignKey = @ForeignKey(
                            name = "fk_user_roles_user"
                    )
            ),
            inverseJoinColumns = @JoinColumn(
                    name = "role_id",
                    nullable = false,
                    foreignKey = @ForeignKey(
                            name = "fk_user_roles_role"
                    )
            ),
            uniqueConstraints = {
                    @UniqueConstraint(
                            name = "uk_user_roles",
                            columnNames = {
                                    "user_id",
                                    "role_id"
                            }
                    )
            }
    )
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

```

### OAuthAccount.java

```java
package com.kavinda.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "oauth_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_oauth_provider_subject",
                        columnNames = {"provider", "provider_subject"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_oauth_accounts_user_id",
                        columnList = "user_id"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_oauth_accounts_user")
    )
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OAuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "provider_email", nullable = false, length = 320)
    private String providerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

```

### Permission.java

```java
package com.kavinda.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_permissions_name",
                        columnNames = "name"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private PermissionName name;

    @Column(nullable = false, length = 255)
    private String description;
}
```

### Role.java

```java
package com.kavinda.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_roles_name",
                        columnNames = "name"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoleName name;

    @Column(nullable = false, length = 255)
    private String description;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(
                    name = "role_id",
                    nullable = false,
                    foreignKey = @ForeignKey(
                            name = "fk_role_permissions_role"
                    )
            ),
            inverseJoinColumns = @JoinColumn(
                    name = "permission_id",
                    nullable = false,
                    foreignKey = @ForeignKey(
                            name = "fk_role_permissions_permission"
                    )
            ),
            uniqueConstraints = {
                    @UniqueConstraint(
                            name = "uk_role_permissions",
                            columnNames = {
                                    "role_id",
                                    "permission_id"
                            }
                    )
            }
    )
    private Set<Permission> permissions = new HashSet<>();
}

```

### OAuthProvider.java

```java
package com.kavinda.auth_service.entity;

public enum OAuthProvider {
    GOOGLE,
    FACEBOOK,
    GITHUB,
    TWITTER,
    LINKEDIN,
    MICROSOFT,
    APPLE,
    UNKNOWN
}

```

### PermissionName.java

```java
package com.kavinda.auth_service.entity;

public enum PermissionName {

    PROFILE_READ("profile:read"),
    PROFILE_UPDATE("profile:update"),

    PROJECT_CREATE("project:create"),
    PROJECT_READ("project:read"),
    PROJECT_UPDATE("project:update"),
    PROJECT_DELETE("project:delete"),

    ANALYTICS_READ("analytics:read"),

    USER_READ("user:read"),
    USER_UPDATE("user:update"),
    USER_DISABLE("user:disable"),

    ROLE_READ("role:read"),
    ROLE_ASSIGN("role:assign"),
    ROLE_REMOVE("role:remove");

    private final String authority;

    PermissionName(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }

}

````

### RoleName.java

```java
package com.kavinda.auth_service.entity;

public enum RoleName {
    USER,
    ADMIN,
    SUPER_ADMIN
}

```

### UserStatus.java

```java
package com.kavinda.auth_service.entity;

public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED
}

````

## Files in Repository Directory

### IAppUserRepository.java

```java
package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IAppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {
            "roles",
            "roles.permissions"
    })
    Optional<AppUser> findWithRolesAndPermissionsById(UUID id);

}

```

### IOAuthAccountRepository.java

```java
package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.OAuthAccount;
import com.kavinda.auth_service.entity.OAuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IOAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<OAuthAccount> findByProviderAndProviderSubject(
            OAuthProvider provider,
            String providerSubject
    );

    @EntityGraph(attributePaths = {
            "user",
            "user.roles",
            "user.roles.permissions"
    })
    Optional<OAuthAccount> findWithUserRolesAndPermissionsByProviderAndProviderSubject(
            OAuthProvider provider,
            String providerSubject
    );
}

```

### IPermissionRepository.java

```java
package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.Permission;
import com.kavinda.auth_service.entity.PermissionName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IPermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(PermissionName name);
}

```

### IRoleRepository.java

```java
package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.Role;
import com.kavinda.auth_service.entity.RoleName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IRoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByName(RoleName name);
}

```

## Files in Security Directory

### AppPrincipal.java

```java
package com.kavinda.auth_service.security;

import com.kavinda.auth_service.entity.OAuthProvider;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public interface AppPrincipal extends Serializable {

    UUID userId();

    String email();

    String displayName();

    OAuthProvider provider();

    Collection<? extends GrantedAuthority> getAuthorities();

    default boolean hasAuthority(String authority) {
        return getAuthorities().stream()
                .anyMatch(grantedAuthority ->
                        Objects.equals(grantedAuthority.getAuthority(), authority)
                );
    }
}

```

### AppOidcPrincipal.java

```java
package com.kavinda.auth_service.security;

import com.kavinda.auth_service.entity.OAuthProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AppOidcPrincipal implements OidcUser, AppPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final String displayName;
    private final OAuthProvider provider;

    private final Map<String, Object> claims;
    private final Collection<? extends GrantedAuthority> authorities;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public AppOidcPrincipal(
            UUID userId,
            String email,
            String displayName,
            OAuthProvider provider,
            Map<String, Object> claims,
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            OidcUserInfo userInfo
    ) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.provider = provider;
        this.claims = Map.copyOf(claims);
        this.authorities = List.copyOf(authorities);
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public UUID userId() {
        return userId;
    }

    @Override
    public String email() {
        return email;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public OAuthProvider provider() {
        return provider;
    }

    /**
     * The Spring Security authentication name.
     * <p>
     * Returning the local user ID makes Authentication#getName()
     * provider-neutral as well.
     */
    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public Map<String, Object> getClaims() {
        return claims;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return claims;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }
}

```

## Files in Service Directory

### CustomOidcUserService.java

```java
package com.kavinda.auth_service.service;

import com.kavinda.auth_service.entity.OAuthProvider;
import com.kavinda.auth_service.mappers.UserAuthorityMapper;
import com.kavinda.auth_service.security.AppOidcPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final GoogleAccountProvisioningService googleAccountProvisioningService;
    private final UserAuthorityMapper userAuthorityMapper;
    private final OidcUserService oidcUserService;

    public CustomOidcUserService(GoogleAccountProvisioningService userProvisioningService, UserAuthorityMapper userAuthorityMapper) {
        this.googleAccountProvisioningService = userProvisioningService;
        this.userAuthorityMapper = userAuthorityMapper;
        this.oidcUserService = new OidcUserService();
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("Loading user for registrationId: {}", registrationId);

        OidcUser oidcUser = this.oidcUserService.loadUser(userRequest);
        log.info("OidcUser loaded: {}", oidcUser);

        return switch (registrationId) {
            case "google" -> provisionGoogleUser(oidcUser, registrationId);
            case "microsoft" -> throw oauthFailure(
                    "provider_not_implemented",
                    "Microsoft authentication is not implemented"
            );
            default -> throw oauthFailure(
                    "unsupported_provider",
                    "Unsupported OAuth2 provider: " + registrationId
            );
        };
    }

    private AppOidcPrincipal provisionGoogleUser(OidcUser oidcUser, String registrationId) {
        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String displayName = resolveDisplayName(oidcUser);
        String picture = oidcUser.getPicture();
        Boolean emailVerifiedClaim = oidcUser.getEmailVerified();

        boolean emailVerified = Boolean.TRUE.equals(emailVerifiedClaim);

        checkClaims(oidcUser, registrationId);

        GoogleUserProfile profile = new GoogleUserProfile(
                subject,
                email,
                displayName,
                picture,
                emailVerified
        );

        var appUser = googleAccountProvisioningService.provisionGoogleUser(profile);

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());

        authorities.addAll(userAuthorityMapper.map(appUser));

        log.info(
                "Google user authenticated: userId={}, roles={}",
                appUser.getId(),
                appUser.getRoles().stream()
                        .map(role -> role.getName().name())
                        .toList()
        );

        return new AppOidcPrincipal(
                appUser.getId(),
                appUser.getEmail(),
                appUser.getDisplayName(),
                OAuthProvider.GOOGLE,
                oidcUser.getClaims(),
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );
    }

    private OAuth2AuthenticationException oauthFailure(
            String code,
            String message
    ) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(code),
                message
        );
    }

    private String resolveDisplayName(OidcUser user) {
        String fullName = user.getFullName();

        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }

        return user.getEmail();
    }

    private void checkClaims(OidcUser oidcUser, String registrationId) {
        if (oidcUser.getSubject() == null || oidcUser.getSubject().isBlank()) {
            throw oauthFailure(
                    "missing_subject",
                    registrationId + " did not provide a valid subject identifier"
            );
        }

        if (oidcUser.getEmail() == null || oidcUser.getEmail().isBlank()) {
            throw oauthFailure(
                    "missing_email",
                    registrationId + " did not provide an email address"
            );
        }

        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw oauthFailure(
                    "email_not_verified",
                    registrationId + " email address is not verified"
            );
        }
    }
}

```

### GoogleAccountProvisioningService.java

```java
package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.UserProfileResponse;
import com.kavinda.auth_service.entity.*;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.repository.IOAuthAccountRepository;
import com.kavinda.auth_service.repository.IRoleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoogleAccountProvisioningService {

    private final IAppUserRepository appUserRepository;
    private final IOAuthAccountRepository oauthAccountRepository;
    private final IRoleRepository roleRepository;

    @Value("${app.authorization.bootstrap-super-admin-email}")
    private String bootstrapSuperAdminEmail;

    @Transactional
    public UserProfileResponse findById(UUID userId) {
        AppUser user = appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user was not found"
                ));

        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission ->
                        permission.getName().authority()
                )
                .collect(Collectors.toUnmodifiableSet());

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.isEmailVerified())
                .status(user.getStatus())
                .roles(roles)
                .permissions(permissions)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional
    public AppUser provisionGoogleUser(GoogleUserProfile profile) {
        // Check if an OAuth account already exists for this Google user
        Optional<OAuthAccount> existingAccount = oauthAccountRepository
                .findByProviderAndProviderSubject(
                        OAuthProvider.GOOGLE,
                        profile.subject()
                );

        if (existingAccount.isPresent()) {
            AppUser existingUser = existingAccount.get().getUser();

            ensureUserCanLogin(existingUser);
            updateUser(existingUser, profile);

            existingAccount.get().setProviderEmail(profile.email());

            appUserRepository.save(existingUser);
            oauthAccountRepository.save(existingAccount.get());

            return loadWithAuthorities(existingUser.getId());
        }

        // If no existing OAuth account is found, check if a user with the same email exists
        AppUser user = appUserRepository
                .findByEmailIgnoreCase(profile.email())
                .orElseGet(() -> createUser(profile));

        ensureUserCanLogin(user);
        updateUser(user, profile);

        if (user.getRoles().isEmpty()) {
            user.getRoles().add(resolveInitialRole(profile.email()));
        }

        AppUser savedUser = appUserRepository.save(user);

        OAuthAccount account = OAuthAccount.builder()
                .user(savedUser)
                .provider(OAuthProvider.GOOGLE)
                .providerSubject(profile.subject())
                .providerEmail(profile.email())
                .build();

        oauthAccountRepository.save(account);

        return loadWithAuthorities(savedUser.getId());
    }

    private AppUser createUser(GoogleUserProfile profile) {
        Role initialRole = resolveInitialRole(profile.email());
        Role defaultRole = DefultRole();

        HashSet<Role> roles = new HashSet<>();
        roles.add(initialRole);
        roles.add(defaultRole);

        return AppUser.builder()
                .email(profile.email())
                .displayName(profile.displayName())
                .avatarUrl(profile.avatarUrl())
                .emailVerified(profile.emailVerified())
                .status(UserStatus.ACTIVE)
                .lastLoginAt(Instant.now())
                .roles(roles)
                .build();
    }

    private void updateUser(
            AppUser user,
            GoogleUserProfile profile
    ) {
        user.setEmail(profile.email());
        user.setDisplayName(profile.displayName());
        user.setAvatarUrl(profile.avatarUrl());
        user.setEmailVerified(profile.emailVerified());
        user.setLastLoginAt(Instant.now());
    }

    private void ensureUserCanLogin(AppUser user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_suspended"),
                    "This user account has been suspended"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_inactive"),
                    "This user account is not active"
            );
        }
    }

    private Role resolveInitialRole(String email) {
        RoleName roleName = isBootstrapSuperAdmin(email)
                ? RoleName.SUPER_ADMIN
                : RoleName.ADMIN;

        return roleRepository
                .findByName(roleName)
                .orElseThrow(() ->
                        new IllegalStateException(
                                roleName + " role is not initialized"
                        )
                );
    }

    private Role DefultRole() {
        return roleRepository
                .findByName(RoleName.USER)
                .orElseThrow(() ->
                        new IllegalStateException(
                                RoleName.USER + " role is not initialized"
                        )
                );
    }

    private boolean isBootstrapSuperAdmin(String email) {
        return bootstrapSuperAdminEmail != null
                && !bootstrapSuperAdminEmail.isBlank()
                && bootstrapSuperAdminEmail.equalsIgnoreCase(email);
    }

    private AppUser loadWithAuthorities(UUID userId) {
        return appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User was not found"
                        )
                );
    }
}

```

### UserSessionService.java

```java
package com.kavinda.auth_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public int revokeAllSessions(UUID userId) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(userId.toString());

        if (sessions.isEmpty()) {
            log.debug(
                    "No active sessions found for userId={}",
                    userId
            );

            return 0;
        }

        sessions.keySet().forEach(sessionRepository::deleteById);

//        for (String sessionId : sessions.keySet()) {
//            sessionRepository.deleteById(sessionId);
//        }

        log.info(
                "Revoked all sessions for userId={}, sessionCount={}",
                userId,
                sessions.size()
        );

        return sessions.size();
    }
}

```

### UserAdministrationService.java

```java
package com.kavinda.auth_service.service;

import com.kavinda.auth_service.dtos.UserStatusUpdateResponse;
import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.RoleName;
import com.kavinda.auth_service.entity.UserStatus;
import com.kavinda.auth_service.exceptions.types.ForbiddenOperationException;
import com.kavinda.auth_service.exceptions.types.ResourceConflictException;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private final IAppUserRepository appUserRepository;
    private final UserSessionService userSessionService;

    @Transactional
    public UserStatusUpdateResponse suspendUser(
            UUID targetUserId,
            AppPrincipal currentPrincipal
    ) {
        AppUser targetUser = findUserWithRoles(targetUserId);

        validateSuspension(targetUser, currentPrincipal);

        if (targetUser.getStatus() == UserStatus.SUSPENDED) {
            throw new ResourceConflictException(
                    "User is already suspended"
            );
        }

        targetUser.setStatus(UserStatus.SUSPENDED);

        /*
         * Writes the status before revoking sessions.
         *
         * The entity is managed, so dirty checking would persist it at
         * transaction commit anyway. saveAndFlush makes the database update
         * happen before the Redis operation.
         */
        appUserRepository.saveAndFlush(targetUser);

        int revokedSessions = userSessionService.revokeAllSessions(targetUserId);

        return UserStatusUpdateResponse.builder()
                .userId(targetUserId)
                .status(UserStatus.SUSPENDED)
                .revokedSessions(revokedSessions)
                .build();
    }

    @Transactional
    public UserStatusUpdateResponse activateUser(
            UUID targetUserId,
            AppPrincipal currentPrincipal
    ) {
        AppUser targetUser = findUserWithRoles(targetUserId);

        validateTargetManagement(targetUser, currentPrincipal);

        if (targetUser.getStatus() == UserStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "User is already active"
            );
        }

        targetUser.setStatus(UserStatus.ACTIVE);
        appUserRepository.saveAndFlush(targetUser);

        /*
         * Activation does not create or restore a session.
         * The user must authenticate with Google again.
         */
        return UserStatusUpdateResponse.builder()
                .userId(targetUserId)
                .status(UserStatus.ACTIVE)
                .revokedSessions(0)
                .build();
    }

    private AppUser findUserWithRoles(UUID userId) {
        return appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );
    }

    private void validateSuspension(
            AppUser targetUser,
            AppPrincipal currentPrincipal
    ) {
        if (targetUser.getId().equals(currentPrincipal.userId())) {
            throw new ForbiddenOperationException(
                    "You cannot suspend your own account"
            );
        }

        validateTargetManagement(
                targetUser,
                currentPrincipal
        );
    }

    private void validateTargetManagement(
            AppUser targetUser,
            AppPrincipal currentPrincipal
    ) {
        boolean currentUserIsSuperAdmin = currentPrincipal.hasAuthority("ROLE_SUPER_ADMIN");

        boolean targetIsSuperAdmin = targetUser
                .getRoles()
                .stream()
                .anyMatch(role -> role.getName() == RoleName.SUPER_ADMIN);

        /*
         * An ADMIN must not suspend or activate a SUPER_ADMIN.
         */
        if (targetIsSuperAdmin && !currentUserIsSuperAdmin) {
            throw new ForbiddenOperationException(
                    "Only a super administrator can manage "
                            + "another super administrator"
            );
        }
    }
}

```

### UserRoleService.java

```java
package com.kavinda.auth_service.service;

import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.Role;
import com.kavinda.auth_service.entity.RoleName;
import com.kavinda.auth_service.exceptions.types.ForbiddenOperationException;
import com.kavinda.auth_service.exceptions.types.ResourceConflictException;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import com.kavinda.auth_service.repository.IAppUserRepository;
import com.kavinda.auth_service.repository.IRoleRepository;
import com.kavinda.auth_service.security.AppPrincipal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final IAppUserRepository appUserRepository;
    private final IRoleRepository roleRepository;

    @Transactional
    public void assignRole(
            UUID targetUserId,
            RoleName roleName,
            AppPrincipal currentPrincipal
    ) {
        verifyRoleManagementPermission(
                roleName,
                currentPrincipal
        );

        AppUser targetUser = findUser(targetUserId);
        Role role = findRole(roleName);

        boolean added = targetUser.getRoles().add(role);

        if (!added) {
            throw new ResourceConflictException(
                    "User already has role: " + roleName
            );
        }
    }

    @Transactional
    public void removeRole(
            UUID targetUserId,
            RoleName roleName,
            AppPrincipal currentPrincipal
    ) {
        verifyRoleManagementPermission(
                roleName,
                currentPrincipal
        );

        AppUser targetUser = findUser(targetUserId);

        if (targetUserId.equals(currentPrincipal.userId()) && roleName == RoleName.SUPER_ADMIN) {
            throw new ForbiddenOperationException(
                    "You cannot remove your own SUPER_ADMIN role"
            );
        }

        boolean removed = targetUser.getRoles()
                .removeIf(role ->
                        role.getName() == roleName
                );

        if (!removed) {
            throw new ResourceNotFoundException(
                    "User does not have role: " + roleName
            );
        }

        if (targetUser.getRoles().isEmpty()) {
            throw new ForbiddenOperationException(
                    "A user must have at least one role"
            );
        }
    }

    private void verifyRoleManagementPermission(
            RoleName roleName,
            AppPrincipal principal
    ) {
        boolean superAdmin = principal.hasAuthority("ROLE_SUPER_ADMIN");

        if ((roleName == RoleName.ADMIN || roleName == RoleName.SUPER_ADMIN) && !superAdmin) {
            throw new ForbiddenOperationException(
                    "Only a super administrator can manage "
                            + roleName + " roles"
            );
        }
    }

    private AppUser findUser(UUID userId) {
        return appUserRepository
                .findWithRolesAndPermissionsById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );
    }

    private Role findRole(RoleName roleName) {
        return roleRepository
                .findByName(roleName)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Role not found: " + roleName
                        )
                );
    }
}

```

### AuthorizationBootstrapService.java

```java
package com.kavinda.auth_service.service;

import com.kavinda.auth_service.entity.*;
import com.kavinda.auth_service.repository.IPermissionRepository;
import com.kavinda.auth_service.repository.IRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationBootstrapService {

    private final IPermissionRepository permissionRepository;
    private final IRoleRepository roleRepository;

    @Transactional
    public void initialize() {
        createPermissions();

        createOrUpdateRole(
                RoleName.USER,
                "Standard PulseTrack user",
                EnumSet.of(
                        PermissionName.PROFILE_READ,
                        PermissionName.PROFILE_UPDATE,
                        PermissionName.PROJECT_CREATE,
                        PermissionName.PROJECT_READ,
                        PermissionName.PROJECT_UPDATE,
                        PermissionName.PROJECT_DELETE,
                        PermissionName.ANALYTICS_READ
                )
        );

        createOrUpdateRole(
                RoleName.ADMIN,
                "PulseTrack administrator",
                EnumSet.of(
                        PermissionName.PROFILE_READ,
                        PermissionName.PROFILE_UPDATE,
                        PermissionName.PROJECT_CREATE,
                        PermissionName.PROJECT_READ,
                        PermissionName.PROJECT_UPDATE,
                        PermissionName.PROJECT_DELETE,
                        PermissionName.ANALYTICS_READ,

                        PermissionName.USER_READ,
                        PermissionName.USER_UPDATE,
                        PermissionName.USER_DISABLE,

                        PermissionName.ROLE_READ
                )
        );

        createOrUpdateRole(
                RoleName.SUPER_ADMIN,
                "PulseTrack super administrator",
                EnumSet.allOf(PermissionName.class)
        );

        log.info("Roles and permissions initialized successfully");
    }

    private void createPermissions() {
        Arrays.stream(PermissionName.values())
                .forEach(permissionName ->
                        permissionRepository.findByName(permissionName)
                                .orElseGet(() -> permissionRepository.save(
                                                Permission.builder()
                                                        .name(permissionName)
                                                        .description(permissionName.authority())
                                                        .build()
                                        )
                                )
                );
    }

    private void createOrUpdateRole(
            RoleName roleName,
            String description,
            Set<PermissionName> permissionNames
    ) {
        Role role = roleRepository
                .findByName(roleName)
                .orElseGet(() ->
                        Role.builder()
                                .name(roleName)
                                .description(description)
                                .build()
                );

        Set<Permission> permissions = permissionNames.stream()
                .map(permissionName ->
                        permissionRepository
                                .findByName(permissionName)
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "Permission is missing: "
                                                        + permissionName
                                        )
                                )
                )
                .collect(Collectors.toSet());

        role.setDescription(description);
        role.setPermissions(permissions);

        roleRepository.save(role);
    }
}
```

## Files in Filters Directory

### OAuth2Filter.java

```java
package com.kavinda.auth_service.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OAuth2Filter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            // Set headers to prevent caching of authenticated responses
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }

        filterChain.doFilter(request, response);
    }
}

```

## Files in Mappers Directory

### UserAuthorityMapper.java

```java
package com.kavinda.auth_service.mappers;

import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.Permission;
import com.kavinda.auth_service.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class UserAuthorityMapper {

    public Set<GrantedAuthority> map(AppUser user) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        for (Role role : user.getRoles()) {
            authorities.add(
                    new SimpleGrantedAuthority(
                            "ROLE_" + role.getName().name()
                    )
            );

            for (Permission permission : role.getPermissions()) {
                authorities.add(
                        new SimpleGrantedAuthority(
                                permission.getName().authority()
                        )
                );
            }
        }

        return Set.copyOf(authorities);
    }
}

```

## Files in Exceptions Directory

- There is a `ErrorResponce` Record that is used to represent the error response.
- Each custom exception has its own class that extends `RuntimeException` and they are in `types` package. The custom
  exceptions are:
    - `ResourceNotFoundException`
    - `ResourceConflictException`
    - `ForbiddenOperationException`

### GlobalExceptionHandler.java

```java
package com.kavinda.auth_service.exceptions;

import com.kavinda.auth_service.exceptions.types.ForbiddenOperationException;
import com.kavinda.auth_service.exceptions.types.ResourceConflictException;
import com.kavinda.auth_service.exceptions.types.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // --------------------------------- custom exception handlers -------------------------------

    // Handles ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // Handles ResourceConflictException
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleResourceConflict(ResourceConflictException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    // Handles ForbiddenOperationException
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperation(ForbiddenOperationException exception) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                exception.getMessage(),
                LocalDateTime.now()
        );

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // ------------------------------- default exception handlers -------------------------------

    // Handles Validation errors (@Valid annotation use by Validation library)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {

        // Grab the first validation error message or combine them
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                errorMessage,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Fallback handler for unexpected generic runtime errors
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(RuntimeException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Fallback handler for unIllegalArgumentException errors
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}

```

## Application Properties

```yml
server:
  port: ${SERVER_PORT:8001}

  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never

  servlet:
    session:
      timeout: 1d
      cookie:
        name: SESSION
        http-only: true
        secure: ${SESSION_COOKIE_SECURE:false}
        same-site: lax
        path: /

spring:
  application:
    name: auth-service

  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5433/auth_service_db}
    username: ${DATABASE_USERNAME:auth_service_user}
    password: ${DATABASE_PASSWORD:auth_service_password}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update

    open-in-view: false
    show-sql: ${JPA_SHOW_SQL:true}

    properties:
      hibernate:
        format_sql: true
        jdbc:
          time_zone: UTC

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DATABASE:0}
      timeout: ${REDIS_TIMEOUT:2s}
      connect-timeout: ${REDIS_CONNECT_TIMEOUT:2s}

  session:
    timeout: ${SESSION_TIMEOUT:1d}
    redis:
      repository-type: indexed
      namespace: pulsetrack:auth:sessions
      flush-mode: on-save
      save-mode: on-set-attribute

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            redirect-uri: "http://localhost:8001/login/oauth2/code/google"
            scope:
              - openid
              - profile
              - email
        provider:
          google:
            issuer-uri: https://accounts.google.com

app:
  frontend-url: ${FRONTEND_URL:http://localhost:3000}

  authorization:
    bootstrap-super-admin-email: ${BOOTSTRAP_SUPER_ADMIN_EMAIL}

logging:
  level:
    root: INFO
    com.kavinda: DEBUG
    org.springframework.web: INFO
    org.springframework.security.oauth2: DEBUG

  file:
    name: ${LOG_FILE:logs/auth-service.log}
```

## Docker Compose File

```yml
services:
  auth-db:
    image: postgres:17-alpine
    container_name: pulse-track-auth-db
    restart: unless-stopped
    environment:
      POSTGRES_USER: auth_service_user
      POSTGRES_PASSWORD: auth_service_password
      POSTGRES_DB: auth_service_db
    ports:
      - "5433:5432"
    volumes:
      - auth_db_data:/var/lib/postgresql/data
    healthcheck:
      test:
        [
          "CMD-SHELL",
          "pg_isready -U auth_service_user -d auth_service_db"
        ]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - pulse-track-network

  adminer:
    image: adminer:latest
    container_name: pulse-track-adminer
    restart: unless-stopped
    ports:
      - "9000:8080"
    depends_on:
      auth-db:
        condition: service_healthy
    networks:
      - pulse-track-network

  redis-session:
    image: redis:8.6.4-alpine
    container_name: pulse-track-redis-session
    restart: unless-stopped
    ports:
      - "6379:6379"
    command:
      - redis-server
      - --appendonly
      - "yes"
      - --maxmemory-policy
      - noeviction
    volumes:
      - redis_session_data:/data
    healthcheck:
      test: [ "CMD", "redis-cli", "ping" ]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - pulse-track-network

  redis-insight:
    image: redis/redisinsight:latest
    container_name: pulse-track-redis-insight
    restart: unless-stopped
    ports:
      - "5540:5540"
    depends_on:
      redis-session:
        condition: service_healthy
    volumes:
      - redis_insight_data:/data
    networks:
      - pulse-track-network

volumes:
  auth_db_data:
  redis_session_data:
  redis_insight_data:

networks:
  pulse-track-network:
    driver: bridge
```

## Pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.kavinda</groupId>
    <artifactId>auth-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>PulseTrack Authentication Service</name>
    <description>
        Authentication, authorization, OAuth2, and Redis-backed session service for PulseTrack
    </description>
    <url/>
    <licenses>
        <license/>
    </licenses>
    <developers>
        <developer/>
    </developers>
    <scm>
        <connection/>
        <developerConnection/>
        <tag/>
        <url/>
    </scm>
    <properties>
        <java.version>25</java.version>
    </properties>
    <dependencies>

        <!-- Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-client</artifactId>
        </dependency>

        <!-- Redis-backed sessions -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-session-data-redis</artifactId>
        </dependency>

        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Persistence -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Development -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-client-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-session-data-redis-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                    <execution>
                        <id>default-compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                    <execution>
                        <id>default-testCompile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>testCompile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```

## envs are load when the application run.
