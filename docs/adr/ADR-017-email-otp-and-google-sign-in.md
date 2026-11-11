# ADR-017: Email + Password + Email OTP, or Google Sign-In (No Phone OTP)

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma | Supersedes: ADR-012

## Context
ADR-012 added phone OTP as a quick sign-in for WhatsApp joiners. Real SMS in India needs DLT registration and costs money per message, and a password-only login is weak if a password leaks. We want two sign-in methods: email + password with a second step that confirms the user owns the email, or Google, which has already verified the email.

## Decision
**Sign-in methods (only these two):**
1. **Email + password + email OTP**
   - `POST /auth/signup {name, email, password, phone?}` → creates the user with `email_verified = false` and emails a verification link (`GET /auth/verify-email?token=…`, single use, 24 h). Clicking it sets `email_verified = true`.
   - `POST /auth/login {email, password}` → if the password is correct and the email is verified, send a 6-digit OTP to the email and return `202`. No tokens yet. An unverified email gets `403 EMAIL_NOT_VERIFIED`, shown only after a correct password.
   - `POST /auth/login/verify-otp {email, otp}` → if the OTP is correct, issue tokens. Login is successful only after this step.
   - The OTP is asked for on **every** password login.
   - Forgot password: `POST /auth/forgot-password {email}` emails a reset OTP; `POST /auth/reset-password {email, otp, newPassword}` sets the password and revokes all of the user's refresh tokens.
2. **Google sign-in (no OTP)**
   - `POST /auth/google {idToken}` → the backend checks the Google ID token's signature, `aud` (our client ID), `iss` and `exp`, and requires `email_verified = true`, then issues tokens straight away.
   - Creates the account on first use. If an account with that email already exists, it is linked (`google_sub`, the Google `sub` claim, is stored on the user). This is safe because Google has verified the email.
   - Google-only users have `password_hash = null`.

**Codes and links:** stored as SHA-256 hashes in the `verification_codes` table with a `purpose` (`EMAIL_VERIFY`, `LOGIN_OTP`, `PASSWORD_RESET`). OTPs are 6 digits from `SecureRandom`, valid 5 min, and 5 wrong attempts kill the code. Issuing a new code marks older unused codes of the same purpose as used. The verification link token is 32 random bytes, valid 24 h. A daily job deletes expired rows. In dev, emails go to Mailpit. Never log OTPs or tokens.

**Rate limits (Redis counters):** 60 s resend cooldown and max 5 sends/hour per email; login max 20/min per IP and 10 per 15 min per email.

**Tokens (unchanged from ADR-012, except the hard 7-day cap):**
- **Access token:** JWT, **15 min**, returned in the response body, kept in memory by the React app, sent as `Authorization: Bearer`.
- **Refresh token:** random 256-bit value in an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie, stored hashed in `refresh_tokens` with a `family_id`. Rotated on every refresh, and reusing an old token revokes the whole family.
- **Max 7 days:** the family expires 7 days after sign-in. Rotation does **not** extend it, so after 7 days the user signs in again (with an email OTP, or with Google).
- **Logout:** revokes the family server-side and clears the cookie.

**Brute force:** rate limits per account/email and per IP return 429, with no account lockout. Login gives the same error for a wrong email and a wrong password.

**Removed:** phone OTP sign-in (`/auth/otp/send` and the phone-based `/auth/otp/verify`) and the phone-keyed Redis keys `otp:{phone}` and `otp:cooldown:{phone}`.

## Alternatives Considered
- Phone OTP (ADR-012): rejected because of DLT registration, SMS cost and SIM-swap risk.
- OTPs in Redis under a `challengeId` (earlier draft of this ADR): replaced by the `verification_codes` table. It survives a Redis restart, is one place to debug, and the load at our scale is negligible. Redis is kept for rate-limit counters only.
- Email OTP to confirm registration (earlier draft): replaced by a verification link, which is one click instead of copying a code.
- Password only, no OTP: rejected because a leaked password alone would be enough to take over an account.
- OTP only on a new device: rejected for MVP because it needs device tracking. It can be added later if the OTP on every login proves annoying.
- OTP after Google sign-in: rejected because Google already verifies the email and can enforce its own 2FA.

## Consequences
- Positive: no SMS cost or DLT work, a stronger password login, and one-tap Google sign-in for WhatsApp joiners.
- Negative: every password login needs an email round-trip, so email delivery (SPF/DKIM, a provider) has to be reliable. Joiners without Google have to register with an email, which is slower than the old phone OTP. Users sign in again at least once a week.
