package com.kavinda.auth_service.repository;

import com.kavinda.auth_service.entity.OAuthAccount;
import com.kavinda.auth_service.entity.OAuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IOAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<OAuthAccount> findByProviderAndProviderSubject(
            OAuthProvider provider,
            String providerSubject
    );
}
