package com.ticktit.auth.service;

import com.ticktit.auth.entity.RefreshToken;
import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.RefreshTokenRepository;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Access tokens (JWT, short) and refresh tokens (random, rotated, max 7 days per sign-in). See ADR-017.
 * A "family" is one sign-in on one device; every rotation keeps the family and its fixed expiry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.access-ttl}")
    private Duration accessTtl;

    @Value("${jwt.refresh-ttl}")
    private Duration refreshTtl;

    /** What the controller sends back: access token in the body, refresh token in an HttpOnly cookie. */
    public record TokenPair(String accessToken, String refreshToken, Instant refreshExpiresAt) {
    }

    /** New sign-in (after OTP or Google): new family, expires refreshTtl from now and never later. */
    @Transactional
    public TokenPair issue(User user) {
        Instant familyExpiresAt = Instant.now().plus(refreshTtl);
        return newPair(user, UUID.randomUUID(), familyExpiresAt);
    }

    /**
     * Rotation: the presented token is marked used and a new one is issued in the same family.
     * Presenting an already-used token means it was copied, so the whole family is revoked.
     * noRollbackFor: the revocation must stay saved even though we then throw.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(Secrets.sha256Hex(rawRefreshToken))  // row locked
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (current.getUsedAt() != null) {
            log.warn("Refresh token reuse detected, revoking family {}", current.getFamilyId());
            refreshTokenRepository.deleteByFamilyId(current.getFamilyId());
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!current.getFamilyExpiresAt().isAfter(Instant.now())) {
            refreshTokenRepository.deleteByFamilyId(current.getFamilyId());
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        User user = userRepository.findById(current.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)   // blocked/deleted users lose their sessions
                .orElse(null);
        if (user == null) {
            refreshTokenRepository.deleteByFamilyId(current.getFamilyId());
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        current.setUsedAt(Instant.now());
        return newPair(user, current.getFamilyId(), current.getFamilyExpiresAt());
    }

    /** Logout on this device. Unknown or already-revoked tokens are ignored: logout always "works". */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(Secrets.sha256Hex(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.deleteByFamilyId(token.getFamilyId()));
    }

    /** Logout everywhere, e.g. after a password reset. */
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private TokenPair newPair(User user, UUID familyId, Instant familyExpiresAt) {
        String rawRefreshToken = Secrets.randomToken();

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(Secrets.sha256Hex(rawRefreshToken));
        token.setFamilyExpiresAt(familyExpiresAt);
        refreshTokenRepository.save(token);

        return new TokenPair(accessToken(user), rawRefreshToken, familyExpiresAt);
    }

    private String accessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ticktit")
                .subject(user.getPublicId().toString())   // public id only, never the internal id
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();   // must match the decoder's HS256 key
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
