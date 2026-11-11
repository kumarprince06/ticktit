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
| POST | `/auth/register` | `{name, email, phone?, password}` | `202 {challengeId}` (OTP emailed) / `409 EMAIL_EXISTS` |
| POST | `/auth/login` | `{email, password}` | `200 {challengeId}` (OTP emailed, no tokens yet) / `401 INVALID_CREDENTIALS` / `429 TOO_MANY_ATTEMPTS` |
| POST | `/auth/otp/verify` | `{challengeId, otp}` | `200 {user, accessToken}` + refresh cookie / `400 OTP_INVALID` / `429` |
| POST | `/auth/otp/resend` | `{challengeId}` | `202` / `429` (60 s cooldown, 5 per hour) |
| POST | `/auth/google` | `{idToken}` | `200 {user, accessToken, isNewUser}` + refresh cookie (no OTP) / `401 GOOGLE_TOKEN_INVALID` |
| POST | `/auth/refresh` | refresh cookie | `200 {accessToken}` + rotated cookie / `401` |
| POST | `/auth/logout` | refresh cookie | `204` (token family revoked, cookie cleared) |
| GET | `/me` | - | `200 {id, name, email, phone, roles}` |

## Movies and Shows (public)

| Method | Path | Response |
|---|---|---|
| GET | `/movies?city=Siliguri&language=Hindi&genre=Action&page=0` | `200 {content:[{id, title, posterUrl, rating, languages}], page, totalElements}` |
| GET | `/movies/{id}` | `200 {id, title, posterUrl, durationMin, rating, synopsis, cast}` |
| GET | `/shows?movieId=1&city=Siliguri&date=2026-11-21` | `200 [{id, theatre, screen, startsAt, prices:{CLASSIC, PRIME, RECLINER}, availableSeats, totalSeats}]` |
| GET | `/shows/{id}/seats` | `200 {rows:[...], seats:[{seatNo, category, pricePaise, status, lockedByMyGroup, assignedTo}]}` - status `AVAILABLE` / `LOCKED` / `BOOKED` |
| GET | `/shows/{id}/suggest?count=4&category=PRIME` | `200 {seats:["B3","B4","B5","B6"]}` or `{seats:null}` |

## Groups

| Method | Path | Request | Response |
|---|---|---|---|
| POST ★ | `/groups` | `{showId}` | `201 {groupId, code, shareLink, whatsappShareUrl}` |
| GET | `/groups/preview/{code}` (public) | - | `200 {movie, theatre, startsAt, memberCount, maxMembers, lockedSeats, status}` - no personal data |
| POST | `/groups/{code}/join` | - | `200 {groupId}` / `400 GROUP_FULL` / `409 GROUP_BOOKED / GROUP_CANCELLED / PAYMENT_IN_PROGRESS` |
| GET | `/groups/{id}` | - | `200 {group, members:[{userId, name, status, seats, sharePaise}], activeBooking:{id, status, lockExpiresAt, paymentDeadline}}` |
| DELETE | `/groups/{id}/members/{userId}` | - | `204` (leader; auto-refund if member PAID) / `409 BOOKING_CONFIRMED` |
| DELETE | `/groups/{id}` | - | `204` (leader; only if no successful payment) / `409 HAS_PAYMENTS` |
| POST | `/groups/{id}/remind/{userId}` | - | `200 {whatsappUrl}` (leader) / `429` if reminded < 1 min ago |

## Bookings

| Method | Path | Request | Response |
|---|---|---|---|
| POST ★ | `/bookings/lock` | `{groupId, seats:["A1","A2"]}` | `200 {bookingId, status:"LOCKED", lockExpiresAt}` / `400 TOO_MANY_SEATS` / `403 NOT_LEADER` / `409 SEATS_UNAVAILABLE {unavailable:[...], alternative:[...]}` / `409 PAYMENT_IN_PROGRESS` / `503 LOCKING_UNAVAILABLE` |
| POST | `/bookings/{id}/assign` | `{assignments:[{seatNo, userId}]}` | `200 {shares:[{userId, sharePaise}]}` (leader, LOCKED only) |
| POST | `/bookings/unlock` | `{groupId}` | `204` (leader, LOCKED only) |
| GET | `/bookings/{id}` | - | `200 {booking, show, seats, members, payments, refunds}` |
| GET | `/bookings/my` | - | `200 [{booking summary}]` upcoming + past, newest first |
| GET | `/bookings/{id}/tickets` | - | `200 {groupQr?, seatQrs:[{seatNo, qrToken, holder}]}` |
| GET | `/bookings/{id}/tickets.pdf` | - | `200 application/pdf` |

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
| 400 | Validation failed / business rule (e.g. `GROUP_FULL`, `SEATS_LESS_THAN_MEMBERS`) |
| 401 | Missing or invalid token |
| 403 | Not allowed (e.g. `NOT_LEADER`) |
| 404 | Not found or not visible to you |
| 409 | Conflict: seats unavailable, group booked, payment in progress, already checked in |
| 422 | Idempotency key reused with a different body |
| 429 | Rate limited (`Retry-After` header) |
| 503 | Dependency down (`LOCKING_UNAVAILABLE`) |
