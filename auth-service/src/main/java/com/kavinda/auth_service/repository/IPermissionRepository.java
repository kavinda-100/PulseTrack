package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.Permission;
import com.kavinda.auth_service.entity.PermissionName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IPermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(PermissionName name);
}
