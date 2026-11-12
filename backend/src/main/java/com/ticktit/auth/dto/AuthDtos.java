package com.ticktit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies for /api/v1/auth. One file, one record per body. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Signup request body. Passwords are capped at 72 characters because BCrypt ignores anything longer. */
    public record SignupRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = "^(\\+91)?[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number")
            String phone,
            @NotBlank
            @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,72}$",
                    message = "must be 8-72 characters with an uppercase letter, a number and a special character")
            String password) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {
    }
}
