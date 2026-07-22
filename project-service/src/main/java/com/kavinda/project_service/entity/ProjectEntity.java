package com.kavinda.project_service.entity;

//projects
//├── id
//├── name
//├── slug
//├── description
//├── owner_user_id
//├── status
//├── environment
//├── created_at
//├── updated_at
//└── version

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "projects",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_projects_slug_owner_user_id",
                        columnNames = {
                                "slug",
                                "owner_user_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_projects_slug",
                        columnList = "slug"
                ),
                @Index(
                        name = "idx_projects_owner_user_id",
                        columnList = "owner_user_id"
                )
        }

)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 225)
    private String slug;

    @Column(name = "description", nullable = true, length = 255)
    private String description;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ProjectStatus status; // ACTIVE, INACTIVE, ARCHIVED

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 50)
    private ProjectEnvironment environment; // DEVELOPMENT, STAGING, PRODUCTION

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if(status == null) {
            status = ProjectStatus.ACTIVE;
        }

        if(environment == null) {
            environment = ProjectEnvironment.DEVELOPMENT;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
