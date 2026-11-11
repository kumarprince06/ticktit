package com.ticktit.auth.repository;

import com.ticktit.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // refresh: lock the row so two refreshes with the same token can't both win (caller must be @Transactional)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    int deleteByFamilyId(UUID familyId);           // logout, or reuse detected

    @Modifying
    int deleteByUserId(Long userId);               // password reset: log out everywhere

    @Modifying
    int deleteByFamilyExpiresAtBefore(Instant now); // daily cleanup job
}
