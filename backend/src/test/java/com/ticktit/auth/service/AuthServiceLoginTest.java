package com.ticktit.auth.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.notification.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Login with email OTP and password reset. EmailService is mocked so the test can read the codes. */
@SpringBootTest
class AuthServiceLoginTest {

    private static final String EMAIL = "login-test@example.com";
    private static final String PASSWORD = "Secret@123";

    @Autowired AuthService authService;
    @Autowired TokenService tokenService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean EmailService emailService;
    @Autowired StringRedisTemplate redis;

    @org.junit.jupiter.api.BeforeEach
    void resetRateLimits() {
        redis.delete(redis.keys("rl:*" + EMAIL + "*"));   // re-running the tests must not hit our own limits
    }

    @BeforeEach
    void activeUser() {
        User user = new User();
        user.setName("Login Test");
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void passwordThenOtpSignsIn() {
        authService.login(EMAIL, PASSWORD);
        AuthService.AuthResult result = authService.verifyLoginOtp(EMAIL, lastLoginOtp());

        assertEquals(EMAIL, result.user().getEmail());
        assertNotNull(result.tokens().accessToken());
        assertNotNull(result.tokens().refreshToken());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() {
        ApiException wrongPassword = assertThrows(ApiException.class, () -> authService.login(EMAIL, "Wrong@123"));
        ApiException unknownEmail = assertThrows(ApiException.class, () -> authService.login("ghost@example.com", PASSWORD));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrongPassword.getErrorCode());
        assertEquals(ErrorCode.INVALID_CREDENTIALS, unknownEmail.getErrorCode());
        verify(emailService, never()).sendLoginOtp(anyString(), anyString(), anyString());
    }

    @Test
    void resendWithoutThePasswordStepSendsNothing() {
        authService.resendLoginOtp(EMAIL);   // no open login code, so this must not email one (would skip the password)
        verify(emailService, never()).sendLoginOtp(anyString(), anyString(), anyString());
    }

    @Test
    void fiveWrongGuessesKillTheCode() {
        authService.login(EMAIL, PASSWORD);
        String code = lastLoginOtp();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> authService.verifyLoginOtp(EMAIL, wrong));
        }

        ApiException ex = assertThrows(ApiException.class, () -> authService.verifyLoginOtp(EMAIL, code));
        assertEquals(ErrorCode.OTP_INVALID, ex.getErrorCode());
    }

    @Test
    void resetChangesPasswordAndSignsOutEverywhere() {
        authService.login(EMAIL, PASSWORD);
        TokenService.TokenPair oldSession = authService.verifyLoginOtp(EMAIL, lastLoginOtp()).tokens();

        authService.forgotPassword(EMAIL);
        ArgumentCaptor<String> resetCode = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(EMAIL), anyString(), resetCode.capture());
        authService.resetPassword(EMAIL, resetCode.getValue(), "NewPass@789");

        assertThrows(ApiException.class, () -> tokenService.refresh(oldSession.refreshToken()));
        assertThrows(ApiException.class, () -> authService.login(EMAIL, PASSWORD));
        assertDoesNotThrow(() -> authService.login(EMAIL, "NewPass@789"));
    }

    @Test
    void forgotPasswordForUnknownEmailIsSilent() {
        assertDoesNotThrow(() -> authService.forgotPassword("ghost@example.com"));
        verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyString());
    }

    private String lastLoginOtp() {
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendLoginOtp(eq(EMAIL), anyString(), otp.capture());
        return otp.getValue();
    }
}
