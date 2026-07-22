package com.kavinda.project_service.entity;

//audit_entries
//├── id
//├── project_id
//├── actor_user_id
//├── action
//├── resource_type
//├── resource_id
//├── metadata_json
//├── correlation_id
//├── created_at

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "audit_entries",
        indexes = {
                @Index(
                        name = "idx_audit_entries_project_id",
                        columnList = "project_id"
                ),
                @Index(
                        name = "idx_audit_entries_actor_user_id",
                        columnList = "actor_user_id"
                ),
                @Index(
                        name = "idx_audit_entries_correlation_id",
                        columnList = "correlation_id"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AuditEntriesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
