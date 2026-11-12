package com.ticktit.auth.service;

import com.ticktit.auth.dto.AuthDtos.SignupRequest;
import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.notification.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Not @Transactional on purpose: the verification email is only sent after the real commit.
 * EmailService is mocked so we can read the link it would have emailed. Test users are deleted afterwards.
 */
@SpringBootTest
class AuthServiceSignupTest {

    private static final String EMAIL = "signup-test@example.com";

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @MockitoBean EmailService emailService;

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);   // codes are deleted by ON DELETE CASCADE
    }

    @Test
    void signupThenVerifyActivatesTheAccount() {
        authService.signup(new SignupRequest("Asha Roy", "Signup-Test@Example.com", "9800000021", "Secret@123"));

        User pending = userRepository.findByEmail(EMAIL).orElseThrow();       // stored lower-case
        assertEquals(UserStatus.PENDING_VERIFICATION, pending.getStatus());
        assertEquals("+919800000021", pending.getPhone());
        assertNotEquals("Secret@123", pending.getPasswordHash());             // hashed, never plain

        authService.verifyEmail(lastEmailedToken());

        User active = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(UserStatus.ACTIVE, active.getStatus());
        assertNotNull(active.getEmailVerifiedAt());
    }

    @Test
    void linkWorksOnlyOnce() {
        authService.signup(new SignupRequest("Asha", EMAIL, null, "Secret@123"));
        String token = lastEmailedToken();
        authService.verifyEmail(token);

        ApiException ex = assertThrows(ApiException.class, () -> authService.verifyEmail(token));
        assertEquals(ErrorCode.LINK_INVALID, ex.getErrorCode());
    }

    @Test
    void newLinkKillsTheOldOne() {
        authService.signup(new SignupRequest("Asha", EMAIL, null, "Secret@123"));
        String oldToken = lastEmailedToken();
        authService.resendVerification(EMAIL);
        String newToken = lastEmailedToken();

        assertThrows(ApiException.class, () -> authService.verifyEmail(oldToken));
        assertDoesNotThrow(() -> authService.verifyEmail(newToken));
    }

    @Test
    void verifiedEmailCannotSignUpAgain() {
        authService.signup(new SignupRequest("Asha", EMAIL, null, "Secret@123"));
        authService.verifyEmail(lastEmailedToken());

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.signup(new SignupRequest("Someone", EMAIL, null, "Other@123")));
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void resendForUnknownEmailSendsNothingAndDoesNotFail() {
        assertDoesNotThrow(() -> authService.resendVerification("nobody@example.com"));
        verify(emailService, never()).sendVerificationLink(anyString(), anyString(), anyString());
    }

    private String lastEmailedToken() {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendVerificationLink(eq(EMAIL), anyString(), link.capture());
        String value = link.getValue();
        return value.substring(value.indexOf("token=") + "token=".length());
    }
}
