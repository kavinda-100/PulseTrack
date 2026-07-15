# Code for Current Authentication implementation

# SecurityConfig.java
```java
package com.kavinda.auth_service.config;

import com.kavinda.auth_service.service.CustomOidcUserService;
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
                )
                // logout configuration
                .logout(logout -> logout
                        .logoutSuccessUrl(frontendUrl)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
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

# CustomOidcUserService.java
```java
package com.kavinda.auth_service.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService oidcUserService;

    public CustomOidcUserService() {
        this.oidcUserService = new OidcUserService();
    }

    @Override
    public @Nullable OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("Loading user for registrationId: {}", registrationId);

        OidcUser oidcUser = this.oidcUserService.loadUser(userRequest);
        log.info("OidcUser loaded: {}", oidcUser);

        String email = oidcUser.getEmail();
        String name = oidcUser.getName();
        log.info("User loaded: email={}, name={}", email, name);

        return oidcUser;
    }
}
```

# OAuth2Filter.java
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

# AuthController.java
```java
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

```

# HomeController.java
```java
package com.kavinda.auth_service.controllers;

import org.springframework.http.ResponseEntity;
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
}

```

# application.yaml
```yaml
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
      timeout: 2s
      connect-timeout: 2s

  session:
    redis:
      namespace: pulsetrack:auth:sessions
      flush-mode: on-save

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

logging:
  level:
    root: INFO
    com.kavinda: DEBUG
    org.springframework.web: INFO
    org.springframework.security.oauth2: DEBUG

  file:
    name: ${LOG_FILE:logs/auth-service.log}
```

