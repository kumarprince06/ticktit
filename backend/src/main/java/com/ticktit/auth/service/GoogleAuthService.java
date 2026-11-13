package com.ticktit.auth.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Google sign-in (ADR-017, STORAGE.md flow C). The app gets an ID token from Google and sends it here;
 * we check it with Google's public keys and sign the user in. No OTP: Google has already proven the email.
 * Switched off until GOOGLE_CLIENT_ID is set.
 */
@Slf4j
@Service
public class GoogleAuthService {

    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final String clientId;

    /** Not a Spring bean on purpose: a second JwtDecoder bean would clash with the one that checks our own tokens. */
    private final NimbusJwtDecoder googleDecoder;

    public GoogleAuthService(UserRepository userRepository, TokenService tokenService,
                             @Value("${google.client-id:}") String clientId) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.clientId = clientId;
        this.googleDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build();   // keys fetched and cached
        this.googleDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),                                          // exp / nbf
                claim(jwt -> GOOGLE_ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss"))), "wrong issuer"),
                claim(jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId), "wrong audience")));
    }

    public record GoogleResult(AuthService.AuthResult result, boolean isNewUser) {
    }

    @Transactional
    public GoogleResult signIn(String idToken) {
        if (clientId.isBlank()) {
            log.warn("Google sign-in attempted but GOOGLE_CLIENT_ID is not set");
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        Jwt jwt;
        try {
            jwt = googleDecoder.decode(idToken);   // signature, expiry, issuer, audience (our client id)
        } catch (JwtException e) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        return signIn(jwt);
    }

    /** After the token is proven genuine. Package-private so tests can call it with a hand-made token. */
    GoogleResult signIn(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (sub == null || email == null || !Boolean.parseBoolean(String.valueOf(jwt.getClaims().get("email_verified")))) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        email = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByGoogleSub(sub).orElse(null);
        boolean isNewUser = false;
        if (user == null) {
            user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setName(nameFrom(jwt, email));
                isNewUser = true;
            }
            user.setGoogleSub(sub);   // link: Google has proven this person owns the email
        }

        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            // a pending password was set by whoever typed this email, never proven to be its owner: drop it
            user.setPasswordHash(null);
            user.setStatus(UserStatus.ACTIVE);
        }
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        if (user.getAvatarUrl() == null) {
            user.setAvatarUrl(jwt.getClaimAsString("picture"));
        }
        user = userRepository.save(user);

        return new GoogleResult(new AuthService.AuthResult(user, tokenService.issue(user)), isNewUser);
    }

    private static String nameFrom(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        return (name == null || name.isBlank()) ? email.substring(0, email.indexOf('@')) : name;
    }

    private static OAuth2TokenValidator<Jwt> claim(java.util.function.Predicate<Jwt> test, String reason) {
        return jwt -> test.test(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_token", reason, null));
    }
}
