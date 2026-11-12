package com.ticktit.auth.controller;

import com.ticktit.auth.dto.AuthDtos.AuthResponse;
import com.ticktit.auth.dto.AuthDtos.EmailRequest;
import com.ticktit.auth.dto.AuthDtos.GoogleRequest;
import com.ticktit.auth.dto.AuthDtos.LoginRequest;
import com.ticktit.auth.dto.AuthDtos.ResetPasswordRequest;
import com.ticktit.auth.dto.AuthDtos.SignupRequest;
import com.ticktit.auth.dto.AuthDtos.UserSummary;
import com.ticktit.auth.dto.AuthDtos.VerifyOtpRequest;
import com.ticktit.auth.entity.User;
import com.ticktit.auth.service.AuthService;
import com.ticktit.auth.service.GoogleAuthService;
import com.ticktit.auth.service.TokenService;
import com.ticktit.auth.service.TokenService.TokenPair;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/** HTTP only: validates input, calls the services, and handles the refresh-token cookie. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** HttpOnly: JavaScript can't read it. Path: the browser sends it only to /api/v1/auth/*. */
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final TokenService tokenService;
    private final GoogleAuthService googleAuthService;

    // ---------------------------------------------------------------- sign-up

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
    }

    @GetMapping("/verify-email")
    public void verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.email());
    }

    // ---------------------------------------------------------------- login

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void login(@Valid @RequestBody LoginRequest request) {
        authService.login(request.email(), request.password());
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<AuthResponse> verifyLoginOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthService.AuthResult result = authService.verifyLoginOtp(request.email(), request.otp());
        return signedIn(result.user(), result.tokens(), null);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleRequest request) {
        GoogleAuthService.GoogleResult google = googleAuthService.signIn(request.idToken());
        return signedIn(google.result().user(), google.result().tokens(), google.isNewUser());
    }

    @PostMapping("/login/resend-otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendLoginOtp(@Valid @RequestBody EmailRequest request) {
        authService.resendLoginOtp(request.email());
    }

    // ---------------------------------------------------------------- session

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        TokenPair tokens = tokenService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), tokens.refreshExpiresAt()).toString())
                .body(new AuthResponse(tokens.accessToken(), null, null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            tokenService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Instant.now()).toString())   // max-age 0 = delete
                .build();
    }

    // ---------------------------------------------------------------- password

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.forgotPassword(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.otp(), request.newPassword());
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<AuthResponse> signedIn(User user, TokenPair tokens, Boolean isNewUser) {
        UserSummary summary = new UserSummary(user.getPublicId(), user.getName(), user.getEmail(), user.getRole().name());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), tokens.refreshExpiresAt()).toString())
                .body(new AuthResponse(tokens.accessToken(), summary, isNewUser));
    }

    private static ResponseCookie refreshCookie(String value, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)           // HTTPS only; browsers treat http://localhost as secure, so dev still works
                .sameSite("Strict")     // never sent on requests started by another site
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }
}
