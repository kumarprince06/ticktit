package com.ticktit.auth.service;

import com.ticktit.auth.dto.AuthDtos.SignupRequest;
import com.ticktit.auth.entity.CodePurpose;
import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.entity.VerificationCode;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.auth.repository.VerificationCodeRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Sign-up and email verification (ADR-017, STORAGE.md flow A). Login and OTP come next. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration VERIFY_LINK_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Creates a PENDING_VERIFICATION user and emails a verification link.
     * An unverified account with the same email is overwritten, so nobody can "squat" an email they do not own.
     */
    @Transactional
    public void signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());

        User user = userRepository.findByEmail(email).orElseGet(User::new);
        if (user.getId() != null && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // two sign-ups with the same email at the same moment: the unique email index decides
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        sendVerificationLink(user);
    }

    /** The link in the email. Single use, 24 h. Activates the account. */
    @Transactional
    public void verifyEmail(String rawToken) {
        VerificationCode code = codeRepository
                .findByCodeHashAndPurposeAndUsedAtIsNull(Secrets.sha256Hex(rawToken), CodePurpose.EMAIL_VERIFY)
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_INVALID));
        User user = userRepository.findById(code.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.LINK_INVALID));

        code.setUsedAt(Instant.now());
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerifiedAt(Instant.now());
        }
    }

    /** Always succeeds from the caller's view, so it cannot be used to find out which emails are registered. */
    @Transactional
    public void resendVerification(String rawEmail) {
        userRepository.findByEmail(normalizeEmail(rawEmail))
                .filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(this::sendVerificationLink);
    }

    private void sendVerificationLink(User user) {
        Instant now = Instant.now();
        codeRepository.markAllCodeUsed(user.getId(), CodePurpose.EMAIL_VERIFY, now);   // old links stop working

        String rawToken = Secrets.randomToken();
        VerificationCode code = new VerificationCode();
        code.setUserId(user.getId());
        code.setCodeHash(Secrets.sha256Hex(rawToken));
        code.setPurpose(CodePurpose.EMAIL_VERIFY);
        code.setExpiresAt(now.plus(VERIFY_LINK_TTL));
        codeRepository.save(code);

        String link = baseUrl + "/verify-email?token=" + rawToken;
        String to = user.getEmail();
        String name = user.getName();
        // only email once the user and code are really saved; a rolled-back sign-up sends nothing
        afterCommit(() -> emailService.sendVerificationLink(to, name, link));
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** "9800000021" and "+919800000021" are stored the same way: +919800000021. */
    static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        return trimmed.startsWith("+91") ? trimmed : "+91" + trimmed;
    }
}
