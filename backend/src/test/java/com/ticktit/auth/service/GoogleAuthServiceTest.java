package com.ticktit.auth.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Google tokens can't be made in a test, so these call signIn(Jwt) with a hand-made token
 * (the step after Google's signature check). The signature/issuer/audience check itself is Spring's.
 */
@SpringBootTest
class GoogleAuthServiceTest {

    private static final String EMAIL = "google-test@example.com";

    @Autowired GoogleAuthService googleAuthService;
    @Autowired UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void firstSignInCreatesAnActiveUser() {
        GoogleAuthService.GoogleResult r = googleAuthService.signIn(googleToken("sub-1", "Google-Test@Example.com", true));

        assertTrue(r.isNewUser());
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals("sub-1", user.getGoogleSub());
        assertNull(user.getPasswordHash());
        assertNotNull(r.result().tokens().accessToken());
    }

    @Test
    void secondSignInFindsTheSameUser() {
        googleAuthService.signIn(googleToken("sub-1", EMAIL, true));
        GoogleAuthService.GoogleResult again = googleAuthService.signIn(googleToken("sub-1", EMAIL, true));

        assertFalse(again.isNewUser());
    }

    @Test
    void pendingPasswordAccountIsActivatedAndItsUnprovenPasswordDropped() {
        User squatter = new User();   // someone signed up with this email but never verified it
        squatter.setName("Squatter");
        squatter.setEmail(EMAIL);
        squatter.setPasswordHash("attacker-chosen-hash");
        userRepository.save(squatter);

        googleAuthService.signIn(googleToken("sub-1", EMAIL, true));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getPasswordHash());
    }

    @Test
    void activePasswordAccountIsLinkedAndKeepsItsPassword() {
        User existing = new User();
        existing.setName("Asha");
        existing.setEmail(EMAIL);
        existing.setPasswordHash("real-hash");
        existing.setStatus(UserStatus.ACTIVE);
        existing.setEmailVerifiedAt(Instant.now());
        userRepository.save(existing);

        GoogleAuthService.GoogleResult r = googleAuthService.signIn(googleToken("sub-1", EMAIL, true));

        assertFalse(r.isNewUser());
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals("sub-1", user.getGoogleSub());
        assertEquals("real-hash", user.getPasswordHash());
    }

    @Test
    void unverifiedGoogleEmailIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> googleAuthService.signIn(googleToken("sub-1", EMAIL, false)));
        assertEquals(ErrorCode.INVALID_GOOGLE_TOKEN, ex.getErrorCode());
    }

    @Test
    void garbageTokenIsRejected() {
        // with no GOOGLE_CLIENT_ID set this is rejected before decoding; with one set, decoding fails
        assertThrows(ApiException.class, () -> googleAuthService.signIn("not-a-google-token"));
    }

    private static Jwt googleToken(String sub, String email, boolean emailVerified) {
        return Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject(sub)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", "Google Test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
