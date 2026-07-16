package com.kavinda.auth_service.config;

import com.kavinda.auth_service.service.AuthorizationBootstrapService;
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