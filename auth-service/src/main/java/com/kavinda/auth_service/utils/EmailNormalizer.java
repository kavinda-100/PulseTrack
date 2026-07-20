package com.kavinda.auth_service.utils;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EmailNormalizer {

    public String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email must not be empty"
            );
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }
}