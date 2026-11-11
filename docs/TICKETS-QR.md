# Ticktit - Tickets and QR Design - v4

Like BookMyShow, every confirmed booking gets QR tickets. Ticktit adds group-specific behaviour: one QR for the whole group **and** one QR per seat, so friends arriving at different times can each enter.

## 1. Ticket Types

| Type | Admits | Who sees it | Created |
|---|---|---|---|
| **Group QR** | All seats of the booking not yet checked in | Leader (and the payer in Pay For All) | 1 per booking, on CONFIRMED |
| **Seat QR** | One seat | The member assigned to that seat (leader sees all) | 1 per seat, on CONFIRMED |

Stored in `tickets (booking_id, kind GROUP/SEAT, unit_ref, holder_user_id, qr_token_id, checked_in_at, revoked_at)`.

## 2. What is inside the QR

Never a plain booking ID (anyone could guess or forge it). The QR encodes a **signed token**:

```
TKT1.<base64url(payload)>.<base64url(HMAC-SHA256(payload, QR_SECRET))>
payload = {"t": "<qr_token_id UUID>", "k": "SEAT", "s": 101, "u": "A2", "v": 1}
```

- `t` = ticket's random UUID (looked up in `tickets`), `k` = kind, `s` = show ID, `u` = seat, `v` = key version (for key rotation).
- Signature lets the scanner reject forged codes instantly, even before the database lookup.
- `QR_SECRET` comes from environment variables; rotate by bumping `v`.
- The QR image is generated on the fly (ZXing) from the token: in the app, in the email, and in the PDF. No QR images stored.

## 3. Check-in (`POST /checkin/scan`, role SCANNER or ADMIN)

1. Verify signature and version → else `400 INVALID_TICKET (FORGED)`.
2. Load ticket by `qr_token_id` → not found or `revoked_at` set → `400 INVALID_TICKET (REVOKED)`.
3. Show must match the scanner's selected show; time within gate window (30 min before start → 15 min after start) → else `400 INVALID_TICKET (WRONG_SHOW / OUTSIDE_WINDOW)`.
4. Booking must be CONFIRMED (not CANCELLED) → else `400 INVALID_TICKET (CANCELLED)`.
5. In one transaction:
   - **Seat QR:** `UPDATE tickets SET checked_in_at = now() WHERE id = ? AND checked_in_at IS NULL` → 0 rows → `409 ALREADY_CHECKED_IN {firstScanAt}`.
   - **Group QR:** check in every seat ticket of the booking that is not yet checked in; return the list admitted now and the list already inside.
6. Response `200 {result: "OK", seats: ["A1","A3"], alreadyInside: ["A2"]}` → scanner shows green with seat numbers.

Every scan (valid or not) is written to `audit_logs` with scanner ID and IP.

## 4. Scanner page (MVP)

- `/scan` in the web app, role SCANNER. Staff select theatre + show, then scan with the phone camera (browser QR library).
- Big green / red result, seat numbers, reason on red, vibration feedback.
- Works on a cheap Android phone; needs internet in MVP (offline mode in Phase 2: verify signature offline, sync check-ins later).

## 5. Delivery

- **In-app:** Success page and My Bookings show my seat QR; the leader also sees the group QR and every seat QR.
- **Email:** sent by the notification consumer on `booking.confirmed`, with the QR image(s) and a link to the PDF.
- **PDF:** `GET /bookings/{id}/tickets.pdf` - movie, theatre, time, seats, QR per seat; generated on first request and cached in file storage.

## 6. v1.1 - Rotating QR and Transfer

**Rotating QR (anti-screenshot):** the in-app QR shows a short-lived token that changes every 30 s:
```
payload adds "w": floor(now / 30s)   // time window
signature = HMAC(payload, QR_SECRET)
```
The scanner accepts the current window ±1 (clock skew). A screenshot stops working within about a minute. Email/PDF QRs stay static for people without the app, so mark them "show the in-app QR if available".

**Ticket transfer:** `POST /tickets/{id}/transfer {toEmail}` before the show → the old seat ticket is revoked (`revoked_at`), a new one is created for the recipient (who signs in with Google or email + password + email OTP). The group QR then admits the new holder's seat as normal. Max 1 transfer per seat; not allowed within 1 h of the show.

## 7. Edge Cases

| Case | Result |
|---|---|
| Group QR scanned, then a seat QR from the same booking | Seat QR → 409 ALREADY_CHECKED_IN (already admitted by the group QR) |
| Seat QR scanned first, group QR later | Group QR admits only the remaining seats |
| Show cancelled after tickets issued | All tickets fail with CANCELLED; refunds already running |
| Screenshot shared (static QR) | First scan wins; second → 409 with first scan time |
| QR_SECRET leaked | Bump key version `v`, re-issue tokens, old version rejected |
