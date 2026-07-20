package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IAppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByNormalizedEmail(String normalizedEmail);

    @EntityGraph(attributePaths = {
            "roles",
            "roles.permissions"
    })
    Optional<AppUser> findWithRolesAndPermissionsById(UUID id);
}
