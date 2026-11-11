# Ticktit - Privacy Note (one page) - v4

Ticktit handles personal data of Indian users, so the Digital Personal Data Protection Act, 2023 (DPDP Act) principles apply: collect only what is needed, use it only for that purpose, keep it safe, and let users see and delete it. This is an engineering note, not legal advice.

## Data We Collect

| Data | Why | Retention |
|---|---|---|
| Name | Show on group page and tickets | While account exists |
| Phone (optional) | Contact for booking issues | While account exists |
| Email | Login, tickets, refunds | While account exists |
| Password hash | Login | While account exists |
| Bookings, payments, refunds (gateway IDs, amounts) | Service + accounting/tax records | Kept as required for accounting even after account deletion (anonymised user link) |
| IP address, request logs | Security, rate limiting | 30 days |

We **do not** collect or store card numbers, UPI PIN, bank details, Aadhaar, or location.

## Who Sees What
- Group members see each other's **first name**, payment status and assigned seat - nothing else (no phone, no email).
- Public group preview shows only movie, show, member count and seat labels.
- Theatre staff (scanner) see seat numbers and ticket validity, not personal data.
- Admins see bookings and payments for support; every admin action is audited.

## User Rights (features to build)
- **See my data:** profile page + My Bookings.
- **Delete my account:** `DELETE /me` → personal fields anonymised, login disabled; payment records kept without personal data.
- **Consent:** sign-up screen links to a short privacy notice; marketing messages only with opt-in (none in MVP).

## Engineering Rules
- Mask phone/email in logs (`98******21`, `p***@gmail.com`).
- Secrets in `.env`, never in git.
- HTTPS everywhere outside local dev.
- If a data breach is suspected: rotate secrets, revoke refresh token families, notify affected users and follow the DPDP breach-reporting rules applicable at the time.
