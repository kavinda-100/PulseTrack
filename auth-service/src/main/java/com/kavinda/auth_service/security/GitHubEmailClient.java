package com.kavinda.auth_service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubEmailClient {

    private static final String GITHUB_ACCEPT_HEADER = "application/vnd.github+json";

    private static final String GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version";

    @Value("${app.github.api-version}")
    private String apiVersion;

    @Value("${app.github.emails-uri}")
    private String emailsUri;

    private final RestClient.Builder restClientBuilder;

    public GitHubEmailResponse getPrimaryVerifiedEmail(String accessToken) {
        GitHubEmailResponse[] emails;

        try {
            emails = restClientBuilder
                    .build()
                    .get()
                    .uri(emailsUri)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessToken
                    )
                    .header(
                            HttpHeaders.ACCEPT,
                            GITHUB_ACCEPT_HEADER
                    )
                    .header(
                            GITHUB_API_VERSION_HEADER,
                            apiVersion
                    )
                    .retrieve()
                    .body(GitHubEmailResponse[].class);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "GitHub email request failed: status={}",
                    exception.getStatusCode()
            );

            throw oauthFailure(
                    "github_email_request_failed",
                    "Could not retrieve email addresses from GitHub"
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Unexpected GitHub email request failure",
                    exception
            );

            throw oauthFailure(
                    "github_email_request_failed",
                    "Could not retrieve email addresses from GitHub"
            );
        }

        if (emails == null || emails.length == 0) {
            throw oauthFailure(
                    "github_email_missing",
                    "GitHub did not provide any email addresses"
            );
        }

        return Arrays.stream(emails)
                .filter(GitHubEmailResponse::primary)
                .filter(GitHubEmailResponse::verified)
                .filter(email -> email.email() != null && !email.email().isBlank())
                .findFirst()
                .orElseThrow(() ->
                        oauthFailure(
                                "github_verified_email_missing",
                                "GitHub does not have a verified primary email"
                        )
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
}
