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
import com.ticktit.common.ratelimit.RateLimiter;
import com.ticktit.notification.EmailService;
import jakarta.annotation.PostConstruct;
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
import java.util.Optional;

/**
 * Sign-up, email verification, login with email OTP, forgot/reset password (ADR-017, STORAGE.md flows A, B, B2).
 * Every wrong-email/wrong-password path looks the same to the caller, so nobody can probe which emails exist.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration VERIFY_LINK_TTL = Duration.ofHours(24);
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int OTP_MAX_PER_HOUR = 5;
    private static final int OTP_MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TokenService tokenService;
    private final RateLimiter rateLimiter;

    @Value("${app.base-url}")
    private String baseUrl;

    /** BCrypt hash of a random value, checked when the email is unknown so both cases take the same time. */
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        dummyHash = passwordEncoder.encode(Secrets.randomToken());
    }

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
                .ifPresent(user -> {
                    try {
                        sendVerificationLink(user);
                    } catch (ApiException throttled) {
                        // stay silent: a 429 here would reveal that the email has a pending account
                    }
                });
    }

    // ---------------------------------------------------------------- login (password, then email OTP)

    /**
     * Step 1: check the password. No tokens yet; a 6-digit code is emailed instead.
     * Wrong email, wrong password, Google-only account and blocked account all give the same 401.
     */
    @Transactional
    public void login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        rateLimiter.check("rl:login:email:" + email, 10, Duration.ofMinutes(15));   // password guessing from many IPs
        User user = userRepository.findByEmail(email).orElse(null);
        String hash = (user != null && user.getPasswordHash() != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(password, hash);   // always runs: same timing for unknown emails

        if (user == null || user.getPasswordHash() == null || !passwordOk
                || user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);         // only shown after a correct password
        }
        sendOtp(user, CodePurpose.LOGIN_OTP, false);
    }

    /** Step 2: check the code. Only now is the user signed in. */
    @Transactional(noRollbackFor = ApiException.class)   // a wrong guess must still count as an attempt
    public AuthResult verifyLoginOtp(String rawEmail, String otp) {
        User user = activeUser(rawEmail).orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID));
        checkOtp(user, CodePurpose.LOGIN_OTP, otp);
        return new AuthResult(user, tokenService.issue(user));
    }

    /**
     * Only works while a login code from a correct password is still open. Without this rule, resend + verify
     * would let anyone sign in with just the email inbox, skipping the password.
     */
    @Transactional
    public void resendLoginOtp(String rawEmail) {
        activeUser(rawEmail)
                .filter(user -> openCode(user, CodePurpose.LOGIN_OTP).isPresent())
                .ifPresent(user -> sendOtp(user, CodePurpose.LOGIN_OTP, true));
    }

    // ---------------------------------------------------------------- forgot / reset password

    /** Always succeeds from the caller's view. Also lets a Google-only user set a first password. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        activeUser(rawEmail).ifPresent(user -> {
            try {
                sendOtp(user, CodePurpose.PASSWORD_RESET, true);
            } catch (ApiException throttled) {
                // stay silent: a 429 here would reveal that the email has an account
            }
        });
    }

    /** New password, and every device is signed out (a thief with an old session loses it too). */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawEmail, String otp, String newPassword) {
        User user = activeUser(rawEmail).orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID));
        checkOtp(user, CodePurpose.PASSWORD_RESET, otp);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokenService.revokeAll(user.getId());
    }

    /** What a successful sign-in gives the controller: who signed in, and their tokens. */
    public record AuthResult(User user, TokenService.TokenPair tokens) {
    }

    // ---------------------------------------------------------------- OTP helpers

    /**
     * Issues and emails a 6-digit code. Limits: one per 60 s and five per hour, per user and purpose.
     * A repeated login inside the 60 s just keeps the code already sent; an explicit resend gets a 429.
     */
    private void sendOtp(User user, CodePurpose purpose, boolean explicitResend) {
        Instant now = Instant.now();
        boolean recentCodeOpen = openCode(user, purpose)
                .filter(code -> code.getCreatedAt().isAfter(now.minus(OTP_RESEND_COOLDOWN)))
                .isPresent();
        if (recentCodeOpen) {
            if (explicitResend) {
                throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
            }
            return;   // e.g. a double-click on "Sign in": the code already in the inbox still works
        }
        if (codeRepository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, now.minus(Duration.ofHours(1)))
                >= OTP_MAX_PER_HOUR) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }

        codeRepository.markAllCodeUsed(user.getId(), purpose, now);   // only the newest code works
        String otp = Secrets.randomOtp();
        VerificationCode code = new VerificationCode();
        code.setUserId(user.getId());
        code.setCodeHash(Secrets.sha256Hex(otp));
        code.setPurpose(purpose);
        code.setExpiresAt(now.plus(OTP_TTL));
        codeRepository.save(code);

        String to = user.getEmail();
        String name = user.getName();
        if (purpose == CodePurpose.LOGIN_OTP) {
            afterCommit(() -> emailService.sendLoginOtp(to, name, otp));
        } else {
            afterCommit(() -> emailService.sendPasswordResetOtp(to, name, otp));
        }
    }

    /** Throws OTP_INVALID unless the newest open code matches. 5 wrong tries kill the code. */
    private void checkOtp(User user, CodePurpose purpose, String otp) {
        VerificationCode code = openCode(user, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID));
        if (!Secrets.hashesEqual(code.getCodeHash(), Secrets.sha256Hex(otp))) {
            code.setAttempts(code.getAttempts() + 1);
            if (code.getAttempts() >= OTP_MAX_ATTEMPTS) {
                code.setUsedAt(Instant.now());
            }
            throw new ApiException(ErrorCode.OTP_INVALID);
        }
        code.setUsedAt(Instant.now());
    }

    /** The newest unused, unexpired code of this kind. */
    private Optional<VerificationCode> openCode(User user, CodePurpose purpose) {
        return codeRepository.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(code -> code.getExpiresAt().isAfter(Instant.now()));
    }

    private Optional<User> activeUser(String rawEmail) {
        return userRepository.findByEmail(normalizeEmail(rawEmail))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE);
    }

    // ---------------------------------------------------------------- shared

    private void sendVerificationLink(User user) {
        // repeated sign-up or resend for one email: max 5 links an hour, so nobody can flood someone's inbox
        rateLimiter.check("rl:verify-mail:" + user.getEmail(), 5, Duration.ofHours(1));
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
