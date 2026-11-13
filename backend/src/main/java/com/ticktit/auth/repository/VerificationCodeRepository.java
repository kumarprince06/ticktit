package com.ticktit.auth.repository;

import com.ticktit.auth.entity.CodePurpose;
import com.ticktit.auth.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    // email link: find the code by its hash and purpose, and ensure it hasn't been used yet
    Optional<VerificationCode> findByCodeHashAndPurposeAndUsedAtIsNull(String codeHash, CodePurpose purpose);

    // OTP: find the most recent unused code for a user and purpose
    Optional<VerificationCode> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(Long userId, CodePurpose purpose);

    // before issuing a new code: kill the old ones
    @Modifying
    @Query("UPDATE VerificationCode v SET v.usedAt = :now WHERE v.userId = :userId AND v.purpose = :purpose AND v.usedAt IS NULL")
    int markAllCodeUsed(Long userId, CodePurpose purpose, Instant now);

    // throttle: how many codes of this kind were sent to the user recently (used ones count too)
    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, CodePurpose purpose, Instant since);

    // daily cleanup job
    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);

}
