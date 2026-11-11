# Ticktit - Software Requirements Specification (SRS) - v4

Version: 4.0 | Style: IEEE 830 | Author: Prince Kumar Sharma | Date: 11 Nov 2026

## 1. Introduction

**Purpose:** single source of truth for developer and tester. The PRD says *what*; the SRS says exactly *how the software must behave*, including errors.

**Scope:** Web app MVP. Backend Spring Boot 3, Java 21. Frontend React. PostgreSQL, Redis, Kafka. Mock payment gateway with the same async interface as Razorpay (ADR-011).

**Conventions**
- Base URL `/api/v1`. User identity always comes from the JWT, never from the request body.
- Money is integer **paise** in the API and database (ADR-009). ₹273.60 = `27360`.
- Times are UTC in the API (ISO-8601), shown in IST in the UI.
- Errors use RFC 9457 Problem Details with a stable `code` (see api.md).
- Every configurable value below lives in `application.yml`, not in code.

### 1.1 Timers and limits (config)

| Key | Value | Meaning |
|---|---|---|
| `lock.ttl` | 5 min | Seats held after lock while the leader starts payment |
| `payment.window` | 10 min | Time for all shares to be paid after Split Pay / Pay For All starts |
| `payment.nudge-at` | 3 min left | Automatic reminder to pending members |
| `payment.cover-prompt-at` | 1 min left | Leader is asked to cover pending members |
| `group.max-members` | 8 | Members per group |
| `booking.max-seats` | 8 | Seats per booking |
| `otp.ttl` / attempts / resend | 5 min / 5 / 60 s cooldown, max 5 per hour per email | Email OTP (ADR-017) |
| `refresh.max-age` | 7 days from sign-in, not extended by rotation | Then sign in again (ADR-017) |
| `login.fail-limit` | 5 per account per 15 min, 20 per IP per 15 min | Then 429 |
| `poll.interval` | 2 s | Seat map + group status polling in the UI |

## 2. Functional Requirements

### FR-01 Auth
- **FR-01.1 Register:** name (min 2 chars), email (valid, unique), phone (optional, 10-digit Indian), password (min 8, 1 upper, 1 number, 1 special). Password stored with BCrypt.
- **FR-01.2** Email already exists → 409 `EMAIL_EXISTS`: "Already registered. Please login." Otherwise a 6-digit OTP is emailed and `202 {challengeId}` is returned; the account becomes ACTIVE (`email_verified_at` set) only after `POST /auth/otp/verify`.
- **FR-01.3 Login (email + password + email OTP):** correct email + password → 6-digit OTP emailed, `200 {challengeId}`, no tokens yet. `POST /auth/otp/verify {challengeId, otp}` → access token (15 min) in the response body + refresh token (max 7 days) in an HttpOnly, Secure, SameSite=Strict cookie. OTP is required on every password login (ADR-017). In dev, OTPs go to Mailpit.
- **FR-01.4 Google sign-in (no OTP):** `POST /auth/google {idToken}` → backend verifies the Google ID token (signature, `aud`, `iss`, `exp`, `email_verified = true`) and returns tokens directly. Creates the account on first use; links to an existing account with the same email. Fastest path for WhatsApp joiners.
- **FR-01.5 Refresh:** `POST /auth/refresh` reads the cookie, rotates the refresh token (old one revoked); the family still expires 7 days after sign-in. Re-use of a revoked refresh token revokes the whole token family.
- **FR-01.6 Logout:** revokes the refresh token server-side and clears the cookie.
- **FR-01.7 Brute force:** more than `login.fail-limit` failures → 429 `TOO_MANY_ATTEMPTS` with `Retry-After`. Message never reveals whether the account exists. (Replaces the v3 account lock / 423.)
- **FR-01.8** All APIs except `/auth/*`, `GET /movies*`, `GET /shows*` and `GET /groups/preview/*` require a JWT. Missing/invalid → 401.

### FR-02 Movies and Shows
- **FR-02.1** `GET /movies?city=Siliguri&language=Hindi&genre=Action&page=0` → movies that have shows in that city, 20 per page.
- **FR-02.2** `GET /movies/{id}` → poster, title, duration, rating, synopsis, cast.
- **FR-02.3** `GET /shows?movieId=&city=&date=` → shows with theatre name, screen, start time, price range by category, available seats, total seats.
- **FR-02.4** No shows → empty list + message "No shows for this date."

### FR-03 Group
- **FR-03.1** `POST /groups {showId}` → group with a 6-char code from `A-Z, 2-9` excluding `O, 0, I, 1` (e.g. `TICK2K`). Leader = creator. Status OPEN. `item_type = SHOW`, `item_id = showId` (ADR-007).
- **FR-03.2** `GET /groups/preview/{code}` (public) → movie, theatre, time, member count, locked seat labels. No names, phones or emails.
- **FR-03.3** `POST /groups/{code}/join` → join. Group full → 400 `GROUP_FULL` "Group is full (max 8)". Group BOOKED → 409 `GROUP_BOOKED`. Group CANCELLED → 409 `GROUP_CANCELLED`. Payment in progress → 409 `PAYMENT_IN_PROGRESS` "Join after the current payment finishes."
- **FR-03.4** `GET /groups/{id}` → members with status JOINED / PAID / PENDING, assigned seats, share amounts, current booking state and timers.
- **FR-03.5** `DELETE /groups/{id}/members/{userId}` (leader, before CONFIRMED) → member removed; their seats unassigned. If the member already PAID → automatic refund + notification.
- **FR-03.6** `DELETE /groups/{id}` (leader) → only if no payment SUCCESS exists; group CANCELLED, active lock released.
- **FR-03.7** Group status: OPEN → BOOKED (a booking CONFIRMED) or CANCELLED. A FAILED or RELEASED booking returns the group to OPEN.

### FR-04 Seat Map and Lock
- **FR-04.1** `GET /shows/{id}/seats` → each seat: `seatNo`, `category`, `pricePaise`, `status` (AVAILABLE / LOCKED / BOOKED), `lockedByMyGroup`, `assignedTo` (first name, only for my group). LOCKED is computed from Redis; Postgres stores only AVAILABLE / BOOKED (ADR-003).
- **FR-04.2** Only the leader can lock, unlock and assign seats. Others → 403 `NOT_LEADER` "Only the leader can select seats."
- **FR-04.3 Seat count rules:** 1 to 8 seats. Split Pay needs every member to have at least 1 seat (seats ≥ members). Pay For All allows any count 1-8 (family case).
- **FR-04.4** `POST /bookings/lock {groupId, seats:["A1","A2"]}` + `Idempotency-Key` → all-or-nothing Redis lock for `lock.ttl` → booking LOCKED, returns `bookingId`, `lockExpiresAt`.
- **FR-04.5** Any seat LOCKED or BOOKED → 409 `SEATS_UNAVAILABLE` with `unavailable:["A1"]` and `alternative:["B3","B4","B5","B6"]` (nearest block of the same size and category, adjacent, centre rows first) or `null` if none.
- **FR-04.6** Only one active booking (LOCKED or PAYMENT_PENDING) per group. Locking again replaces the LOCKED booking (old seats released) but is rejected while PAYMENT_PENDING (409 `PAYMENT_IN_PROGRESS`).
- **FR-04.7** `POST /bookings/{id}/assign {assignments:[{seatNo, userId}]}` → leader assigns seats to members (default: in join order). Allowed while LOCKED.
- **FR-04.8** `POST /bookings/unlock {groupId}` → leader releases before payment starts → booking RELEASED.
- **FR-04.9** Lock expiry: a scheduler (every 15 s) marks LOCKED bookings past `lock_expires_at` as RELEASED. Redis TTL frees the seats themselves (ADR-013).
- **FR-04.10** If Redis is unavailable, lock calls return 503 `LOCKING_UNAVAILABLE`. No database fallback (ADR-015).

### FR-05 Payment and Booking
- **FR-05.1 Price:** each seat costs `seatPrice + 2000 fee + 360 fee GST` paise. Total = sum over seats. A member's share = sum over their assigned seats (fair split by seat). Prices are frozen in `price_snapshot` at lock time (ADR-009).
- **FR-05.2** `POST /payments/split {bookingId}` (leader, Idempotency-Key) → booking PAYMENT_PENDING, `payment_deadline = now + payment.window`, Redis lock TTL extended to deadline + 60 s, share per member returned.
- **FR-05.3** `POST /payments/pay {bookingId}` + `Idempotency-Key` (one key per payment attempt) → server computes the caller's share, creates a payment PENDING with a gateway order, returns `{paymentId, gatewayOrderId, amountPaise, status: PENDING}`. Same key + same body → same response, no new order. Same key + different body → 422 `IDEMPOTENCY_KEY_REUSED`.
- **FR-05.4** Payment becomes SUCCESS or FAILED **only** from a signature-verified gateway webhook (`POST /webhooks/payment`) or a server-side status check - never from the browser (ADR-011). MVP mock gateway: VPA `success@mock` → success, `failure@mock` → failure, webhook fired after ~1 s.
- **FR-05.5** After FAILED, the member may retry with a **new** Idempotency-Key while the window is open.
- **FR-05.6** When paid total = booking total → in one transaction: seats set BOOKED with a conditional update (all rows must match), booking CONFIRMED, group BOOKED, tickets created, outbox event `booking.confirmed`. If the seat update does not match every seat → booking FAILED, everyone refunded (should never happen; alert).
- **FR-05.7** `POST /payments/pay-for-all {bookingId}` (leader) → one payment for the full total; on SUCCESS all members marked PAID. Allowed while LOCKED (starts the window) or PAYMENT_PENDING (pays the remaining unpaid amount only).
- **FR-05.8 Deadline:** scheduler finds PAYMENT_PENDING past `payment_deadline` → booking FAILED → seats released → refund every SUCCESS payment → outbox `booking.failed`.
- **FR-05.9 Late payment:** a SUCCESS webhook for a booking that is no longer PAYMENT_PENDING → payment kept as SUCCESS and refunded automatically (reason `LATE_PAYMENT`).
- **FR-05.10** `GET /bookings/{id}` → movie, theatre, time, seats with assignees, members, payment status per member, timers.
- **FR-05.11 (v1.1) Cover a friend:** `POST /payments/cover {bookingId, beneficiaryUserId}` → payer pays the beneficiary's share; beneficiary marked PAID; IOU recorded.

### FR-06 My Bookings and Notifications
- **FR-06.1** `GET /bookings/my` → upcoming + past, newest first, with refund status where relevant.
- **FR-06.2** Booking confirmed → email to every member with QR (Kafka consumer) + in-app notice.
- **FR-06.3** Payment success → in-app + email to that member: "Payment ₹273.60 successful."
- **FR-06.4** `POST /groups/{id}/remind/{userId}` (leader) → in-app + email reminder to the member, and returns a `wa.me` link with a prefilled message containing the member's personal pay link. Max 1 remind per member per minute.
- **FR-06.5** Automatic nudge at `payment.nudge-at`; leader cover prompt at `payment.cover-prompt-at`.
- **FR-06.6** SMS is logged only in MVP (real SMS needs TRAI DLT registration - Phase 2).

### FR-07 Tickets and QR (details in TICKETS-QR.md)
- **FR-07.1** On CONFIRMED: one ticket per seat (`seat QR`) + one `group QR` for the booking. QR contains a signed token, never a plain booking ID.
- **FR-07.2** Members see their own seat QRs; the leader sees all and the group QR. Pay For All bookings: the leader holds all QRs.
- **FR-07.3** `POST /checkin/scan {qrToken}` (role SCANNER/ADMIN) → verifies signature, show, time window (gates open 30 min before show, close 15 min after start) → marks the seat(s) checked in. Second scan → 409 `ALREADY_CHECKED_IN` with first scan time.
- **FR-07.4** Ticket PDF download per booking.
- **FR-07.5 (v1.1)** Rotating QR in the app (token refreshes every 30 s); ticket transfer to another user before the show.

### FR-08 Admin (minimal, role ADMIN)
- **FR-08.1** Create/update movies, theatres, screens with seat layout, shows with category prices.
- **FR-08.2** `POST /admin/shows/{id}/cancel` → every CONFIRMED booking CANCELLED, 100% refund including fee, notifications; active LOCKED/PAYMENT_PENDING bookings FAILED with refunds.
- **FR-08.3** Search bookings; view payments and refunds; retry a FAILED refund.
- **FR-08.4** Every admin action written to `audit_logs`.
- **FR-08.5** Seed script loads cities, theatres, movies, 7 days of shows (RUNBOOK.md).

## 3. Non-Functional Requirements

| ID | Area | Requirement |
|---|---|---|
| NFR-01 | Performance | Seat map < 1 s; API p95 < 300 ms; lock API p95 < 100 ms |
| NFR-02 | Concurrency | 100 concurrent lock requests on one seat → exactly 1 wins (automated test) |
| NFR-03 | Security | BCrypt; JWT Bearer access + HttpOnly refresh cookie; no secrets/passwords/OTPs in logs; JPA only; webhook signatures verified; strict CORS; rate limits (THREAT-MODEL.md) |
| NFR-04 | Reliability | 0 double booking, 0 double charge, 0 lost refunds (outbox + retries + reconciliation) |
| NFR-05 | Usability | Mobile-first, join via link < 30 s, no install, works on 360 px screens |
| NFR-06 | Availability | 99% for MVP; Redis/Kafka outage fails safely (503 / delayed events), never inconsistently |
| NFR-07 | Observability | JSON logs with `requestId` across Kafka; Actuator health (DB, Redis, Kafka); metrics for locks, payments, refunds, outbox lag |
| NFR-08 | Privacy | Store only name, email, phone; mask phone/email in logs; delete account on request (PRIVACY.md) |
| NFR-09 | Maintainability | Flyway migrations; ≥ 70% service-layer coverage; OpenAPI at `/swagger-ui` |

## 4. Edge Cases

| ID | Case | Expected |
|---|---|---|
| EC-01 | Two groups lock the same seat in the same second | One succeeds; other 409 with alternative block |
| EC-02 | Pay clicked twice in 1 s | Same Idempotency-Key → one payment, second call returns the first result |
| EC-03 | Leader closes tab after lock | Seats free after 5 min; booking RELEASED by scheduler |
| EC-04 | Leader removes a PAID member | Refund + seat unassigned |
| EC-05 | Internet drops during payment | Payment PENDING until webhook; retry with the same key returns the same payment |
| EC-06 | 9th member tries to join | 400 `GROUP_FULL` |
| EC-07 | Split Pay with fewer seats than members | 400 `SEATS_LESS_THAN_MEMBERS` |
| EC-08 | Payment with `failure@mock` | FAILED; member retries with a new key |
| EC-09 | Webhook arrives twice | Second ignored (unique gateway event ID) |
| EC-10 | Payment succeeds after the deadline | Auto-refund `LATE_PAYMENT` |
| EC-11 | Redis down | Lock → 503; existing bookings unaffected |
| EC-12 | Kafka down | Booking still confirms; events wait in outbox |
| EC-13 | Same QR scanned twice | 409 `ALREADY_CHECKED_IN` |
| EC-14 | Join during payment window | 409 `PAYMENT_IN_PROGRESS` |

## 5. UI Requirements

- **Home:** movie list, search, city selector (Siliguri default).
- **Movie detail:** poster, shows by theatre, "Book with friends" (create group) and "Book solo".
- **Seat map:** rows A-J, legend Available (green) / Locked (yellow) / Booked (grey) / Your group (outlined), names on assigned seats, countdown timer, alternative-block suggestion on conflict.
- **Group page:** members, assigned seats, share per member, PAID/PENDING badges, Pay button, Remind (WhatsApp), Cover (v1.1), countdown.
- **Success:** group QR + my seat QR, download PDF, share.
- **Scanner page (staff):** camera scan, green/red result, seat list.
