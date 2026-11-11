# ADR-012: Short Access Token + Rotating Refresh Cookie + Phone OTP

Status: Superseded by ADR-017 (2026-11-11) | Date: 2026-11-11 | Author: Prince Kumar Sharma | Supersedes: ADR-005

## Context
ADR-005 said "access token 24h in an HttpOnly cookie, refresh token in Phase 2", while SRS/API used a Bearer header with a refresh token now and client-side logout. A 24-hour token cannot be revoked. Also, WhatsApp-first joining (UNIQUE-FEATURES #5) needs a sign-in faster than email + strong password.

## Decision
- **Access token:** JWT, 15 min, returned in the response body, kept in memory by the React app, sent as `Authorization: Bearer`.
- **Refresh token:** random 256-bit value, 7 days, in an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. Stored hashed in `refresh_tokens` with a `family_id`. Rotated on every refresh; reuse of an old token revokes the whole family (stolen-token detection).
- **Logout:** revokes the family server-side and clears the cookie.
- **Phone OTP sign-in:** 6 digits, stored hashed in Redis for 5 min, 5 attempts, 60 s resend cooldown, max 5 sends/hour per phone. Creates the account on first use. MVP logs the OTP (dev) instead of sending SMS (DLT registration needed for real SMS in India).
- **Brute force:** rate limits per account and per IP returning 429, instead of locking accounts (v3's 423 lock let anyone lock out a victim and revealed which emails exist).

## Alternatives Considered
- Long-lived JWT only - rejected: cannot revoke.
- Server sessions - rejected: stateful, harder for mobile later.
- Access token in a cookie - rejected for MVP: needs CSRF protection on every call; Bearer + in-memory is simpler and the refresh cookie is limited to the auth path.

## Consequences
- Positive: revocable sessions, fast WhatsApp join, no account lockout attacks.
- Negative: refresh flow and token table to build; the app must silently refresh every 15 min.
