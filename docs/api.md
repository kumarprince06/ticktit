# Ticktit - API Contract - v4

- Base URL: `http://localhost:8080/api/v1`
- Auth: `Authorization: Bearer <accessToken>` on every endpoint except `/auth/*`, `GET /movies*`, `GET /shows*`, `GET /groups/preview/*` and `/webhooks/*`. Missing/invalid → `401`.
- The user always comes from the JWT, never from the body.
- Money: integer paise (`27360` = ₹273.60). Times: ISO-8601 UTC.
- `Idempotency-Key: <uuid>` is **required** on endpoints marked ★. One key per user action/attempt. Same key + same body → stored response; same key + different body → `422 IDEMPOTENCY_KEY_REUSED`. Keys kept 24 h.
- Live data: the UI polls `GET /shows/{id}/seats` and `GET /groups/{id}` every 2 s (WebSocket in Phase 2).
- OpenAPI: `/v3/api-docs`, Swagger UI: `/swagger-ui`.

## Auth

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/auth/signup` | `{name, email, phone?, password}` | `201` (verification link emailed) / `409 EMAIL_ALREADY_EXISTS` / `400 VALIDATION_FAILED` |
| GET | `/auth/verify-email?token=` | - | `200` (account ACTIVE) / `400 LINK_INVALID` (bad, used or expired) |
| POST | `/auth/resend-verification` | `{email}` | `202` always / `429` |
| POST | `/auth/login` | `{email, password}` | `202` (OTP emailed, no tokens yet) / `401 INVALID_CREDENTIALS` / `403 EMAIL_NOT_VERIFIED` / `429 TOO_MANY_REQUESTS` |
| POST | `/auth/login/verify-otp` | `{email, otp}` | `200 {user, accessToken}` + refresh cookie / `400 OTP_INVALID` / `429` |
| POST | `/auth/login/resend-otp` | `{email}` | `202` / `429` (60 s cooldown, 5 per hour) |
| POST | `/auth/forgot-password` | `{email}` | `202` always / `429` |
| POST | `/auth/reset-password` | `{email, otp, newPassword}` | `204` (all sessions logged out) / `400 OTP_INVALID` |
| POST | `/auth/google` | `{idToken}` | `200 {user, accessToken, isNewUser}` + refresh cookie (no OTP) / `401 INVALID_GOOGLE_TOKEN` |
| POST | `/auth/refresh` | refresh cookie | `200 {accessToken}` + rotated cookie / `401` |
| POST | `/auth/logout` | refresh cookie | `204` (token family revoked, cookie cleared) |
| GET | `/me` | - | `200 {id, name, email, phone, roles}` |

## Movies and Shows (public)

No sign-in needed. Prices are in paise (ADR-009). Movies are addressed by slug. Show times are UTC instants; the app shows them in IST.

| Method | Path | Response |
|---|---|---|
| GET | `/cities` | `200 [{slug, name, state}]` |
| GET | `/movies?city=siliguri&status=NOW_SHOWING&language=Hindi&genre=Action&q=kite&page=0&size=20` | `200 {content:[{slug, title, tagline, certification, genres, languages, durationMin, releaseDate, posterUrl, status}], page, size, totalElements}`. `NOW_SHOWING` (default) = has an upcoming show in the city; `COMING_SOON` ignores the city |
| GET | `/movies/{slug}` | `200 {slug, title, tagline, synopsis, certification, genres, languages, formats, durationMin, releaseDate, cast:[{name, role, character}], posterUrl, trailerUrl, status}` / `404 NOT_FOUND` |
| GET | `/shows?movie=paper-kites&city=siliguri&date=2026-11-21` | `200 [{id, theatre:{slug, name, area}, screen, language, format, startsAt, prices:{CLASSIC, PRIME, RECLINER}, availableSeats, totalSeats}]`, only shows not yet started, date in the city's time zone |
| GET | `/shows/{id}/seats` | `200 {show:{...}, prices:{...}, seats:[{seatNo, row, col, gridRow, gridCol, category, pricePaise, status}]}`. status `AVAILABLE` / `BOOKED` / `BLOCKED`; `LOCKED`, `lockedByMyGroup` and `assignedTo` are added by the booking module |
| GET | `/shows/{id}/suggest?count=4&category=PRIME` | Moved to the booking module (needs the Redis seat locks) |

## Groups

Group first, seats later (PRD journey 1): the organiser makes a group for a show, shares the link, friends join, then seats are locked for everyone (Bookings). A single booking is a group of one that pays for all.

| Method | Path | Request | Response |
|---|---|---|---|
| POST ★ | `/groups` | `{showId}` | `201 {groupId, code, shareLink, whatsappShareUrl}` / `409 SHOW_NOT_BOOKABLE` |
| GET | `/groups/preview/{code}` (public) | - | `200 {code, status, memberCount, maxMembers, item:{title, venue, city, detail, startsAt, link, bookable}}`, no names or personal data / `404` |
| POST | `/groups/{code}/join` | - | `200 {groupId}` (joining twice is harmless) / `400 GROUP_FULL` / `409 GROUP_BOOKED / GROUP_CANCELLED` |
| GET | `/groups/my` | - | `200 [{id, code, status, memberCount, youAreLeader, item}]` newest first |
| GET | `/groups/{id}` | - | `200 {id, code, status, maxMembers, youAreLeader, item, members:[{userId, name, avatarUrl, role, status, you}], shareLink, whatsappShareUrl}` / `404` if you're not a member |
| DELETE | `/groups/{id}/members/{userId}` | - | `204`: the organiser removes someone, or a member removes themselves (leave) / `403 NOT_LEADER` / `409 LEADER_CANNOT_LEAVE` |
| DELETE | `/groups/{id}` | - | `204` organiser cancels / `403 NOT_LEADER` |
| POST | `/groups/{id}/remind/{userId}` | - | Comes with the booking module (needs the payment window) |

`{code}` is 6 characters without look-alikes (no 0/O, 1/I/L), case-insensitive. `{id}` and `{userId}` are public UUIDs.

## Bookings

Money-changing calls need `Idempotency-Key: <uuid>` (ADR-010): one per user action, reused on retries. Same key + same body returns the stored response; same key + different body is `422 IDEMPOTENCY_KEY_REUSED`; missing is `400 IDEMPOTENCY_KEY_REQUIRED`. A failed call does not use up its key.

| Method | Path | Request | Response |
|---|---|---|---|
| POST ★ | `/bookings/lock` | `{groupId, seats:["F7","F8"]}` | `200 BookingView` (LOCKED, 5 min) / `400 TOO_MANY_SEATS` (max 8) / `403 NOT_LEADER` / `409 SEATS_UNAVAILABLE {unavailable:[...], alternative:[...] or null}` / `409 PAYMENT_IN_PROGRESS` / `409 SHOW_NOT_BOOKABLE` / `503 LOCKING_UNAVAILABLE` (Redis down: fails closed). Locking again replaces the group's seats; the new seats are taken first, so a failed re-lock keeps the old ones |
| POST | `/bookings/unlock` | `{groupId}` | `204` (organiser; nothing to do if no lock) / `409 PAYMENT_IN_PROGRESS` |
| POST | `/bookings/{id}/assign` | `{assignments:[{seatNo, userId}]}` | `200 BookingView` (organiser, LOCKED only) / `400 INVALID_ASSIGNMENT` / `409 BOOKING_NOT_LOCKED` |
| GET | `/groups/{groupId}/booking` | - | `200 BookingView` / `204` when the group has no current booking (members only) |
| GET | `/shows/{id}/seats?group={groupId}` | - | Seat map with live status `AVAILABLE` / `LOCKED` / `BOOKED` / `BLOCKED`, and `heldByYou` for that group's seats |

`BookingView` = `{id, code, status, lockExpiresAt, paymentDeadline, totalPaise, youAreLeader, seats:[{seatNo, category, ticketPricePaise, payablePaise, assignee}], shares:[{member, seats, sharePaise}]}`. Per seat payable = ticket (GST inside) + ₹20 fee + 18% GST on the fee (PAYMENT.md). Default seating: one seat per member in join order, extras to the organiser. A single booking ("Book for myself") is a group of one.

Coming with later modules: `GET /bookings/my`, `/bookings/{id}/tickets`, `/bookings/{id}/tickets.pdf`.

## Payments

| Method | Path | Request | Response |
|---|---|---|---|
| POST ★ | `/payments/split` | `{bookingId}` | `200 {status:"PAYMENT_PENDING", paymentDeadline, shares:[{userId, sharePaise}]}` (leader) / `400 SEATS_LESS_THAN_MEMBERS` |
| POST ★ | `/payments/pay` | `{bookingId}` | `201 {paymentId, gatewayOrderId, amountPaise, status:"PENDING", checkout:{...}}` / `409 ALREADY_PAID` / `409 WINDOW_CLOSED` |
| POST ★ | `/payments/pay-for-all` | `{bookingId}` | `201 {paymentId, gatewayOrderId, amountPaise (remaining unpaid), status:"PENDING"}` (leader) |
| POST ★ | `/payments/cover` (v1.1) | `{bookingId, beneficiaryUserId}` | `201 {paymentId, amountPaise, status:"PENDING"}` |
| GET | `/payments/{id}` | - | `200 {paymentId, status, amountPaise}` (UI polls after checkout) |
| POST | `/webhooks/payment` | gateway event, header `X-Signature` (HMAC-SHA256 of raw body) | `200` always after storing; `400` on bad signature |
| POST | `/mock-gateway/orders/{orderId}/complete` (dev profile only) | `{vpa: "success@mock" or "failure@mock"}` | `202` - mock fires the webhook ~1 s later |

## Check-in (role SCANNER or ADMIN)

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/checkin/scan` | `{qrToken}` | `200 {result:"OK", show, seats}` / `409 ALREADY_CHECKED_IN {firstScanAt}` / `400 INVALID_TICKET {reason}` |

## Admin (role ADMIN)

| Method | Path | Purpose |
|---|---|---|
| POST/PUT | `/admin/movies`, `/admin/theatres`, `/admin/screens`, `/admin/shows` | Catalog management (shows include category prices) |
| POST ★ | `/admin/shows/{id}/cancel` | Cancel show → refunds |
| GET | `/admin/bookings?query=&status=` | Search bookings |
| POST ★ | `/admin/refunds/{id}/retry` | Retry a FAILED refund |
| GET | `/admin/audit-logs` | Audit trail |

## Errors (RFC 9457 Problem Details)

```json
{
  "type": "https://ticktit.in/errors/seats-unavailable",
  "title": "Seats unavailable",
  "status": 409,
  "code": "SEATS_UNAVAILABLE",
  "detail": "A1 is already locked. Please select others.",
  "unavailable": ["A1"],
  "alternative": ["B3", "B4", "B5", "B6"],
  "requestId": "01JD3K9Q..."
}
```

| Status | Meaning |
|---|---|
| 400 | Validation failed (`VALIDATION_FAILED` + per-field `errors`), unreadable request (`MALFORMED_REQUEST`), business rule (e.g. `GROUP_FULL`, `SEATS_LESS_THAN_MEMBERS`) |
| 405 | Wrong HTTP method for the URL (`METHOD_NOT_ALLOWED`) |
| 401 | Missing or invalid token |
| 403 | Not allowed (e.g. `NOT_LEADER`) |
| 404 | Not found or not visible to you |
| 409 | Conflict: seats unavailable, group booked, payment in progress, already checked in |
| 422 | Idempotency key reused with a different body |
| 429 | Rate limited (`Retry-After` header) |
| 503 | Dependency down (`LOCKING_UNAVAILABLE`) |
| 500 | Unexpected error (`INTERNAL_SERVER_ERROR`). Body never contains internal details; quote the `requestId` to find the logged stack trace |

All codes live in the `ErrorCode` enum (`com.ticktit.common.enums`). The code string is the contract with the frontend: never rename one that is in use.
