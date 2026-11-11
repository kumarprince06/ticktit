# Ticktit - Threat Model (one page) - v4

Method: list what an attacker wants, how they would try, and what stops them. Review before every release.

| # | Threat | Example | Mitigation |
|---|---|---|---|
| T1 | Pay less than the real price | Edit amount in the request | Server computes amount from `booking_units`; client amount ignored (PAYMENT.md §6) |
| T2 | Fake a successful payment | Call our API saying "paid" | Status only from HMAC-verified webhook or server `fetchPayment` (ADR-011) |
| T3 | Replay a webhook | Resend an old SUCCESS event | `webhook_events` unique event ID; amount + order ID cross-check |
| T4 | Double refund | Retry refund calls | One refund per payment (unique), gateway reference `refund-{id}`, idempotent consumer |
| T5 | Steal seats / block a show | Script that locks many seats | Leader-only, max 8 seats, 1 active booking per group, rate limit on lock (10/min/user), max 3 open groups per user |
| T6 | See other people's groups/bookings (IDOR) | Change the ID in the URL | Public UUIDs; every query filtered by membership; 404 when not a member |
| T7 | Account takeover by guessing passwords/OTP | Brute force | BCrypt + email OTP on every password login, login rate limits (429), OTP 5 attempts + expiry + cooldown, generic error messages |
| T8 | Lock out a victim | Fail logins on their email | No account lock (ADR-012); per-IP limits stop the attacker instead |
| T9 | Stolen refresh token | XSS or device theft | HttpOnly cookie, rotation with reuse detection revokes the family, 15-min access tokens |
| T10 | XSS | Script in group name / user name | React escaping, input validation (length, charset), CSP header |
| T11 | Forged / reused ticket | Make or share a QR | Signed tokens, one-time check-in, rotating QR in v1.1 (ADR-016) |
| T12 | Spam OTP emails (cost/harassment) | Loop on `/auth/signup`, `/auth/resend-verification`, `/auth/login/resend-otp` or `/auth/forgot-password` | OTP sent only after a correct password on login; per-email and per-IP limits; 60 s cooldown; CAPTCHA if abused |
| T13 | Leak personal data in logs | Phones, emails, tokens in logs | Masking filter; never log OTPs, tokens, passwords |
| T14 | Secrets in git | Gateway keys, JWT/QR secrets | `.env` only, `.env.example` without values, secret scanning in CI |
| T15 | Dependency vulnerabilities | Old library CVE | OWASP Dependency-Check in CI, Dependabot |
| T16 | Admin abuse | Refund to self | ADMIN role, audit log of every admin action, refunds only to the original payer |

Out of scope for MVP: DDoS protection beyond rate limits (use the hosting provider's), payment card security (handled by the gateway).
