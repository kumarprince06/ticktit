package com.ticktit.auth.service;

import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.RefreshTokenRepository;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.auth.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Once a day, removes auth rows nobody can use any more, so the tables don't grow forever. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCleanupJob {

    private final VerificationCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // ponytail: every instance runs it; the deletes are idempotent, so that's harmless. Add ShedLock if it gets heavy.
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")   // 03:30 IST, quietest hour
    @Transactional
    public void run() {
        Instant now = Instant.now();
        // keep a day of expired codes: the per-hour OTP limit counts recent ones
        int codes = codeRepository.deleteByExpiresAtBefore(now.minus(Duration.ofDays(1)));
        int tokens = refreshTokenRepository.deleteByFamilyExpiresAtBefore(now);
        // sign-ups never verified within 7 days: frees the email for its real owner (STORAGE.md flow A)
        int users = userRepository.deleteByStatusAndCreatedAtBefore(UserStatus.PENDING_VERIFICATION, now.minus(Duration.ofDays(7)));
        log.info("Auth cleanup: {} codes, {} refresh tokens, {} unverified users deleted", codes, tokens, users);
    }
}
