package com.kavinda.project_service.entity;

//project_settings
//├── id
//├── project_id
//├── retention_days
//├── max_batch_size
//├── strict_schema_validation
//├── allow_unknown_events
//├── mask_ip_addresses
//├── allowed_origins_json
//├── created_at
//├── updated_at
//└── version

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "project_settings",
        indexes = {
                @Index(
                        name = "idx_project_settings_project_id",
                        columnList = "project_id"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ProjectSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "max_batch_size")
    private Integer maxBatchSize;

    @Column(name = "strict_schema_validation")
    private Boolean strictSchemaValidation;

    @Column(name = "allow_unknown_events")
    private Boolean allowUnknownEvents;

    @Column(name = "mask_ip_addresses")
    private Boolean maskIpAddresses;

    @Column(name = "allowed_origins_json", columnDefinition = "TEXT")
    private String allowedOriginsJson;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
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