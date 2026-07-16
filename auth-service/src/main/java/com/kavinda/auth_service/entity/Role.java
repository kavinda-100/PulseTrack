package com.kavinda.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_roles_name",
                        columnNames = "name"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoleName name;

    @Column(nullable = false, length = 255)
    private String description;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(
                    name = "role_id",
                    nullable = false,
                    foreignKey = @ForeignKey(
                            name = "fk_role_permissions_role"
                    )
            ),
            inverseJoinColumns = @JoinColumn(
                    name = "permission_id",
                    nullable = false,
                    foreignKey = @ForeignKey(
                            name = "fk_role_permissions_permission"
                    )
            ),
            uniqueConstraints = {
                    @UniqueConstraint(
                            name = "uk_role_permissions",
                            columnNames = {
                                    "role_id",
                                    "permission_id"
                            }
                    )
            }
    )
    private Set<Permission> permissions = new HashSet<>();
}
