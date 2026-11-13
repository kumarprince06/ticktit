package com.ticktit.auth.service;

import com.ticktit.auth.entity.RefreshToken;
import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.RefreshTokenRepository;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against the local Postgres (needs DB_USER, DB_PASSWORD, JWT_SECRET). Each test is rolled back. */
@SpringBootTest
@Transactional
class TokenServiceTest {

    @Autowired TokenService tokenService;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    User user;

    @BeforeEach
    void createUser() {
        user = new User();
        user.setName("Test User");
        user.setEmail("token-test@example.com");
        user.setPasswordHash("not-a-real-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user = userRepository.save(user);
    }

    @Test
    void issueGivesAJwtThatOurDecoderAccepts() {
        TokenService.TokenPair pair = tokenService.issue(user);

        Jwt jwt = jwtDecoder.decode(pair.accessToken());
        assertEquals(user.getPublicId().toString(), jwt.getSubject());
        assertEquals("USER", jwt.getClaimAsString("role"));
        assertNotNull(pair.refreshToken());
        // only the hash is stored
        assertTrue(refreshTokenRepository.findByTokenHash(Secrets.sha256Hex(pair.refreshToken())).isPresent());
    }

    @Test
    void refreshRotatesAndKeepsTheFamilyExpiry() {
        TokenService.TokenPair first = tokenService.issue(user);
        TokenService.TokenPair second = tokenService.refresh(first.refreshToken());

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertEquals(first.refreshExpiresAt(), second.refreshExpiresAt());   // rotation never extends the session
        RefreshToken old = refreshTokenRepository.findByTokenHash(Secrets.sha256Hex(first.refreshToken())).orElseThrow();
        assertNotNull(old.getUsedAt());
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeFamily() {
        TokenService.TokenPair first = tokenService.issue(user);
        TokenService.TokenPair second = tokenService.refresh(first.refreshToken());
        rotatedAgo(first, TokenService.REUSE_GRACE.plusSeconds(5));   // reuse well after the rotation = theft

        ApiException ex = assertThrows(ApiException.class, () -> tokenService.refresh(first.refreshToken()));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, ex.getErrorCode());
        // the legitimate newest token is gone too: the attacker and the victim must both sign in again
        assertThrows(ApiException.class, () -> tokenService.refresh(second.refreshToken()));
    }

    @Test
    void reuseRightAfterRotationIsALostResponseNotTheft() {
        // e.g. the user reloaded the page before the browser saved the new cookie
        TokenService.TokenPair first = tokenService.issue(user);
        TokenService.TokenPair second = tokenService.refresh(first.refreshToken());

        TokenService.TokenPair retried = assertDoesNotThrow(() -> tokenService.refresh(first.refreshToken()));
        assertEquals(first.refreshExpiresAt(), retried.refreshExpiresAt());   // same family, same 7-day cap
        assertDoesNotThrow(() -> tokenService.refresh(retried.refreshToken()));  // the session goes on
        assertDoesNotThrow(() -> tokenService.refresh(second.refreshToken()));   // nothing was revoked
    }

    private void rotatedAgo(TokenService.TokenPair pair, java.time.Duration ago) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(Secrets.sha256Hex(pair.refreshToken())).orElseThrow();
        token.setUsedAt(Instant.now().minus(ago));
        refreshTokenRepository.saveAndFlush(token);
    }

    @Test
    void expiredFamilyIsRejected() {
        TokenService.TokenPair pair = tokenService.issue(user);
        RefreshToken token = refreshTokenRepository.findByTokenHash(Secrets.sha256Hex(pair.refreshToken())).orElseThrow();
        token.setFamilyExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.saveAndFlush(token);

        assertThrows(ApiException.class, () -> tokenService.refresh(pair.refreshToken()));
    }

    @Test
    void blockedUserCannotRefresh() {
        TokenService.TokenPair pair = tokenService.issue(user);
        user.setStatus(UserStatus.BLOCKED);
        userRepository.saveAndFlush(user);

        assertThrows(ApiException.class, () -> tokenService.refresh(pair.refreshToken()));
    }

    @Test
    void logoutRevokesTheFamilyAndUnknownTokenIsIgnored() {
        TokenService.TokenPair pair = tokenService.issue(user);
        tokenService.logout(pair.refreshToken());

        assertThrows(ApiException.class, () -> tokenService.refresh(pair.refreshToken()));
        assertDoesNotThrow(() -> tokenService.logout("not-a-real-token"));
    }

    @Test
    void revokeAllLogsOutEveryDevice() {
        TokenService.TokenPair phone = tokenService.issue(user);
        TokenService.TokenPair laptop = tokenService.issue(user);
        tokenService.revokeAll(user.getId());

        assertThrows(ApiException.class, () -> tokenService.refresh(phone.refreshToken()));
        assertThrows(ApiException.class, () -> tokenService.refresh(laptop.refreshToken()));
    }
}
