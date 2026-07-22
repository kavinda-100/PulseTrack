package com.kavinda.project_service.entity;

//api_keys
//├── id
//├── project_id
//├── name
//├── key_prefix
//├── key_hash
//├── status
//├── environment
//├── created_by_user_id
//├── last_used_at
//├── expires_at
//├── revoked_at
//├── created_at
//├── updated_at
//└── version

//ACTIVE
//REVOKED
//EXPIRED
//
//DEVELOPMENT
//STAGING
//PRODUCTION

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "api_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_api_keys_project_id_key_prefix",
                        columnNames = {
                                "project_id",
                                "key_prefix"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_api_keys_project_id_key_hash",
                        columnNames = {
                                "project_id",
                                "key_hash"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_api_keys_project_id",
                        columnList = "project_id"
                ),
                @Index(
                        name = "idx_api_keys_key_prefix",
                        columnList = "key_prefix"
                ),
                @Index(
                        name = "idx_api_keys_key_hash",
                        columnList = "key_hash"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class APIKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 100)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private APIKeyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 50)
    private APIKeyEnvironment environment;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        if(status == null) {
            status = APIKeyStatus.ACTIVE;
        }

        if(environment == null) {
            environment = APIKeyEnvironment.DEVELOPMENT;
        }

        if(expiresAt != null && expiresAt.isBefore(now)) {
            status = APIKeyStatus.EXPIRED;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}
