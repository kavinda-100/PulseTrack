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
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_app_users_normalized_email",
                        columnNames = "normalized_email"
                )
        },
        indexes = {
                @Index(
                        name = "idx_app_users_email",
                        columnList = "email"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Canonical PulseTrack account email.
     * <p>
     * Linking another provider must not silently replace this field.
     */
    @Column(nullable = false, length = 320)
    private String email;

    @Column(
            name = "normalized_email",
            nullable = false,
            length = 320
    )
    private String normalizedEmail;

    /**
     * PulseTrack-level profile data.
     * <p>
     * Provider-specific profile data belongs to OAuthAccount.
     */
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
                            columnNames = {"user_id", "role_id"}
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