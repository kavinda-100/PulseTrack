package com.kavinda.project_service.entity;

//project_members
//├── id
//├── project_id
//├── user_id
//├── role
//├── status
//├── added_by_user_id
//├── joined_at
//├── created_at
//├── updated_at
//└── version


//OWNER
//ADMIN
//DEVELOPER
//VIEWER

//(project_id, user_id) UNIQUE

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "project_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_members_project_id_user_id",
                        columnNames = {
                                "project_id",
                                "user_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_project_members_project_id",
                        columnList = "project_id"
                ),
                @Index(
                        name = "idx_project_members_user_id",
                        columnList = "user_id"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ProjectMembersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "added_by_user_id", nullable = false)
    private UUID addedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private ProjectMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ProjectMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        if(joinedAt == null) {
            joinedAt = now;
        }

        if(role == null) {
            role = ProjectMemberRole.DEVELOPER;
        }

        if(status == null) {
            status = ProjectMemberStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}
