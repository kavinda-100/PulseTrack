package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.Role;
import com.kavinda.auth_service.entity.RoleName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IRoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByName(RoleName name);
}
