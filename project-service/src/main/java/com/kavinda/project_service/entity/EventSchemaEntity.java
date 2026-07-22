package com.kavinda.project_service.entity;

//event_schemas
//├── id
//├── project_id
//├── event_name
//├── description
//├── schema_json
//├── status
//├── version_number
//├── created_by_user_id
//├── created_at
//├── updated_at
//└── version

//(project_id, event_name) UNIQUE

//ACTIVE
//DISABLED

// `schema_json` example:
//    {
//        "type": "object",
//        "required": ["orderId", "currency", "amount"],
//        "properties": {
//        "orderId": {
//        "type": "string"
//        },
//        "currency": {
//        "type": "string"
//        },
//        "amount": {
//        "type": "number"
//        }
//        },
//        "additionalProperties": false
//    }

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "event_schemas",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_schemas_project_id_event_name",
                        columnNames = {
                                "project_id",
                                "event_name"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_event_schemas_project_id",
                        columnList = "project_id"
                ),
                @Index(
                        name = "idx_event_schemas_event_name",
                        columnList = "event_name"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class EventSchemaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "event_name", nullable = false, length = 255)
    private String eventName;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EventSchemaStatus status;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

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
        version = 1L;

        if (versionNumber == null) {
            versionNumber = 1;
        }

        if (status == null) {
            status = EventSchemaStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }

}