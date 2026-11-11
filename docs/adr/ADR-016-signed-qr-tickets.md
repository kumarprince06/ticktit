# ADR-016: Signed QR Tickets (Group + Seat), One-Time Check-in

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma

## Context
v3 said "QR encodes Booking ID". IDs are guessable, so anyone could make a valid-looking QR. Groups also need flexibility: friends arrive at different times, but families want one code.

## Decision
- Each confirmed booking gets one **group ticket** and one **seat ticket per seat** (`tickets` table).
- The QR contains a signed token `TKT1.<payload>.<HMAC-SHA256>` with a random ticket UUID, kind, show, seat and key version. Secret from environment; rotation by version.
- QR images are generated on the fly (ZXing), not stored.
- Check-in: verify signature → load ticket → check show, time window, booking status → atomic `UPDATE ... WHERE checked_in_at IS NULL`. Second scan → 409 with first scan time. Group QR admits only seats not yet inside.
- v1.1: rotating in-app QR (30 s time window in the signed payload) against screenshot resale; ticket transfer revokes the old ticket.

## Alternatives Considered
- Plain booking ID or URL in QR - rejected: forgeable.
- JWT as QR payload - works, but long JWTs make dense QR codes that scan poorly on cheap phones; a compact custom token is smaller.
- One QR per booking only - rejected: friends arriving separately could not enter.

## Consequences
- Positive: forgery-proof, one-time entry, works for both friends and families, ready for anti-screenshot rotation.
- Negative: a scanner page and role to build; secret management for QR signing.
