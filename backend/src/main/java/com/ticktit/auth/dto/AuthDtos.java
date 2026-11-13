package com.ticktit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request and response bodies for /api/v1/auth. One file, one record per body. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Passwords are capped at 72 characters because BCrypt ignores anything longer. */
    private static final String PASSWORD_RULE = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,72}$";
    private static final String PASSWORD_MESSAGE =
            "must be 8-72 characters with an uppercase letter, a number and a special character";

    /** Signup request body. */
    public record SignupRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = "^(\\+91)?[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number")
            String phone,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    /** No password rules here: login only checks the password, it never tells which rule a guess breaks. */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record VerifyOtpRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be the 6-digit code") String otp) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be the 6-digit code") String otp,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record GoogleRequest(@NotBlank @Size(max = 4096) String idToken) {
    }

    /**
     * Sent after a successful sign-in or refresh. The refresh token goes in an HttpOnly cookie, never here.
     * isNewUser is only set by Google sign-in (the app may show a welcome screen); null elsewhere.
     */
    public record AuthResponse(String accessToken, UserSummary user, Boolean isNewUser) {
    }

    public record UserSummary(UUID id, String name, String email, String role) {
    }
}
