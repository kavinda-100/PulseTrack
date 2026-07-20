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
                        columnNames = {
                                "provider",
                                "provider_subject"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_oauth_user_provider",
                        columnNames = {
                                "user_id",
                                "provider"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_oauth_accounts_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_oauth_provider_email",
                        columnList = "provider_email"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_oauth_accounts_user"
            )
    )
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OAuthProvider provider;

    /**
     * Stable provider-side identity.
     * <p>
     * Google: OIDC sub
     * GitHub: numeric GitHub user ID
     */
    @Column(
            name = "provider_subject",
            nullable = false,
            length = 255
    )
    private String providerSubject;

    @Column(
            name = "provider_email",
            nullable = false,
            length = 320
    )
    private String providerEmail;

    @Column(
            name = "provider_email_verified",
            nullable = false
    )
    private boolean providerEmailVerified;

    @Column(name = "provider_username", length = 255)
    private String providerUsername;

    @Column(name = "provider_display_name", length = 255)
    private String providerDisplayName;

    @Column(name = "provider_avatar_url", length = 1000)
    private String providerAvatarUrl;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if (lastLoginAt == null) {
            lastLoginAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}