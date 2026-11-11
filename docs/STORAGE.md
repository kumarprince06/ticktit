# Ticktit - Storage Design - v4

## 1. Overview - 3 Storages, 3 Jobs

| Storage | Holds | Why |
|---|---|---|
| **PostgreSQL** (main DB) | Users, catalog, groups, bookings, payments, refunds, tickets, outbox | Must never lose money data. ACID transactions, unique + check constraints |
| **Redis** (cache + lock) | Seat locks, OTPs, rate-limit counters, seat-map cache, revoked token families | Fast (< 10 ms), automatic expiry (TTL), atomic Lua scripts. Nothing here is the source of truth |
| **File storage** (local → S3) | Movie posters, ticket PDFs | Files are big; do not store in DB. MVP `backend/uploads/` served via `/uploads/*`; Phase 2 S3 + CloudFront with the same paths |

QR images are **not** stored: they are rendered on the fly from the signed token (TICKETS-QR.md).

## 2. PostgreSQL Tables

All tables also have `id BIGINT` identity PK and `created_at TIMESTAMPTZ DEFAULT now()`. Constraints: db-design.md.

### Identity (auth) - ADR-017
| Table | Columns |
|---|---|
| users (V1) | public_id UUID (the only id the API and JWT expose), name, email (stored lower-case), email_verified_at, phone (optional, `+91XXXXXXXXXX`, not used for login), password_hash (BCrypt, null for Google-only users), google_sub (Google's stable user ID, null if never used Google), avatar_url (from Google), role USER/ADMIN/SCANNER, status PENDING_VERIFICATION/ACTIVE/BLOCKED/DELETED, updated_at |
| verification_codes (V1) | user_id, code_hash (SHA-256 of the OTP or link token), purpose EMAIL_VERIFY/LOGIN_OTP/PASSWORD_RESET, attempts, expires_at, used_at |
| refresh_tokens (V1) | user_id, family_id UUID, token_hash (SHA-256), family_expires_at (sign-in + 7 days, never extended), used_at (set on rotation) |
| login_events (later) | user_id (null if the email is unknown), email_hash (SHA-256, for rate-limit analysis without storing unknown emails), method PASSWORD/EMAIL_OTP/GOOGLE, result SUCCESS/BAD_PASSWORD/BAD_OTP/OTP_EXPIRED/RATE_LIMITED/GOOGLE_INVALID/BLOCKED, ip, user_agent. Kept 90 days |
| user_consents (later) | user_id, purpose TERMS/PRIVACY/MARKETING_EMAIL/MARKETING_WHATSAPP, granted BOOLEAN, policy_version, ip. Append-only: a new row on every change, the latest row wins (DPDP Act proof of consent) |

Later columns on `users` (added with their feature): `home_city_id`, `last_login_at`, `deleted_at`.

**Codes:** OTPs and link tokens are stored only as SHA-256 hashes in `verification_codes`. No raw code is ever written to a table or a log. A daily job deletes expired codes and expired refresh families.

#### Signup and sign-in, step by step

**A. Sign up with email + password**
1. `POST /auth/signup {name, email, phone?, password}`.
2. If an `ACTIVE`/`BLOCKED` user has this email → `409 EMAIL_EXISTS`.
   If a `PENDING_VERIFICATION` user has it → overwrite that row (name, password_hash). This stops someone "squatting" an email they do not own.
3. Insert/update `users`: status `PENDING_VERIFICATION`, `email_verified_at = null`, `password_hash = BCrypt(password)`.
4. `verification_codes`: SHA-256 of a 32-byte random token, purpose `EMAIL_VERIFY`, expires in 24 h. Email the link `/auth/verify-email?token=…`.
5. `GET /auth/verify-email?token=…` → status `ACTIVE`, `email_verified_at = now()`, code `used_at = now()`. No tokens: the user logs in next.
6. `POST /auth/resend-verification {email}` → older link codes marked used, new link emailed. Always `202`.
7. Scheduler deletes `PENDING_VERIFICATION` users older than 7 days.

**B. Login with email + password**
1. `POST /auth/login {email, password}` → check rate limits → load user by `lower(email)`.
2. Unknown email or wrong password → BCrypt still runs (same timing), `401 INVALID_CREDENTIALS`. `BLOCKED` → same 401 (do not reveal).
3. Correct password but `PENDING_VERIFICATION` → `403 EMAIL_NOT_VERIFIED` (shown only after a correct password, so it reveals nothing to a guesser).
4. Correct and `ACTIVE` → older `LOGIN_OTP` codes marked used, new 6-digit OTP hashed into `verification_codes` (5 min), emailed, `202`.
5. `POST /auth/login/verify-otp {email, otp}` → 5 wrong tries kills the code → success: new refresh family (`family_expires_at = now() + 7 days`), tokens.

**B2. Forgot password**
1. `POST /auth/forgot-password {email}` → `PASSWORD_RESET` OTP emailed if the account exists. Always `202`.
2. `POST /auth/reset-password {email, otp, newPassword}` → new `password_hash`, all of the user's refresh tokens deleted (logged out everywhere).

**C. Google sign-in (no OTP)**
1. `POST /auth/google {idToken}` → verify signature, `aud`, `iss`, `exp`, `email_verified = true`.
2. Find user by `google_sub` → found: sign in.
3. Else find by `lower(email)` → found: set `google_sub`, `email_verified_at` if null, status `ACTIVE` (a pending email+password account becomes verified, because Google proved the email). Sign in.
4. Else create: name + avatar from Google, `password_hash = null`, status `ACTIVE`, `email_verified_at = now()`, consents. `isNewUser = true`.
5. A Google-only user can add a password later through "Set password" (email OTP required).

**D. Refresh / logout / delete account**
- Refresh: look up `token_hash` (`FOR UPDATE`) → reject if `family_expires_at` passed → set old `used_at`, insert a new row in the same family. Reuse of a token whose `used_at` is set → delete the whole family.
- Logout: delete the family's rows. "Log out of all devices": delete every refresh token of the user.
- Delete account (PRIVACY.md): status `DELETED`, `deleted_at`, name → "Deleted user", email → `deleted+{publicId}@ticktit.invalid`, phone/google_sub/password_hash/avatar → null, all families revoked. Bookings, payments and invoices are kept (tax law: 8 years) but no longer point to a real person.

**Sample rows**
```
users
 id | public_id    | name         | email                | email_verified_at   | phone         | password_hash | google_sub       | status
 1  | 7c1e...a9    | Prince Kumar | prince@example.com   | 2026-11-11 10:02:11 | +919800000021 | $2a$12$Qe...  | null             | ACTIVE
 2  | 0b44...3f    | Rahul Das    | rahul@gmail.com      | 2026-11-11 10:05:40 | null          | null          | 1043728811920031 | ACTIVE
 3  | 91d2...77    | Sneha Roy    | sneha@example.com    | null                | null          | $2a$12$Lp...  | null             | PENDING_VERIFICATION

refresh_tokens
 user_id | family_id | token_hash | family_expires_at   | used_at
 1       | 5f0e...   | 1b2d...    | 2026-11-18 10:02:11 | 2026-11-11 10:17:30   (rotated)
 1       | 5f0e...   | 9a7c...    | 2026-11-18 10:02:11 | null                  (current)
```

### Catalog (movie)
Seat **layout** belongs to a screen (drawn once). Seat **status** belongs to a show (copied from the layout when the show is published).

| Table | Columns |
|---|---|
| cities | name, slug, state, state_code (GST code, e.g. 19 = West Bengal), timezone (default Asia/Kolkata), is_active |
| theatres | city_id, name, slug, address_line, area, pincode, lat, lng, gstin, amenities TEXT[] (PARKING/WHEELCHAIR/FOOD/M_TICKET), cancellation_allowed, status ACTIVE/INACTIVE |
| screens | theatre_id, name (Audi 3), screen_type STANDARD/IMAX/4DX, sound (DOLBY_7_1/DOLBY_ATMOS), total_seats, layout_version |
| screen_seats | screen_id, seat_no (H7), row_label (H), col_no (7), grid_row, grid_col (position on the drawn map, so gaps and aisles show), category CLASSIC/PRIME/RECLINER, seat_type NORMAL/WHEELCHAIR/COUPLE, is_active (false = broken seat) |
| movies | slug, title, synopsis, certification U/UA7/UA13/UA16/A, duration_min, release_date, languages TEXT[], formats TEXT[] (2D/3D/IMAX_2D/IMAX_3D/4DX), genres TEXT[], cast JSONB `[{name, role ACTOR/DIRECTOR/MUSIC, character}]`, poster_url, banner_url, trailer_url, status COMING_SOON/NOW_SHOWING/ENDED |
| shows | movie_id, screen_id, language (Hindi), format (2D), starts_at, ends_at (start + runtime + ads/cleaning), booking_opens_at, status SCHEDULED/CANCELLED, cancel_reason |
| show_prices | show_id, category, price_paise (GST-inclusive ticket price shown to users) |
| show_seats | show_id, screen_seat_id, seat_no, category (copied for fast queries), status AVAILABLE/BOOKED/BLOCKED (BLOCKED = held by the theatre or broken), booking_id, version |

**Sample rows**
```
cities        19 | Siliguri | West Bengal | state_code 19
theatres      Cineplex One - City Centre | Uttorayon, Matigara | 734010 | 26.7271, 88.3953 | 19ABCDE1234F1Z5
screens       Audi 3 | STANDARD | DOLBY_7_1 | 182 seats
screen_seats  H7 | row H, col 7 | grid (8, 9) | PRIME | NORMAL
movies        the-last-ride | "The Last Ride" | UA13 | 152 min | 2026-11-07 | {Hindi,Bengali} | {2D} | NOW_SHOWING
shows         The Last Ride | Audi 3 | Hindi | 2D | 2026-11-14 19:30 → 22:10 IST | SCHEDULED
show_prices   CLASSIC 18000 | PRIME 25000 | RECLINER 45000   (paise)
show_seats    show 101 | H7 | PRIME | AVAILABLE
```

### Groups and bookings (core)
| Table | Columns |
|---|---|
| groups | public_id, code (6-char unique), item_type SHOW, item_id, leader_id, status OPEN/BOOKED/CANCELLED, max_members 8 |
| group_members | group_id, user_id, role LEADER/MEMBER, status JOINED/PAID/PENDING, joined_at |
| bookings | public_id, booking_code (8 chars, e.g. TK7Q2M9A, shown on tickets), group_id, item_type, item_id, status LOCKED/PAYMENT_PENDING/CONFIRMED/RELEASED/FAILED/CANCELLED, mode SPLIT/PAY_FOR_ALL (null until payment starts), total_paise, paid_paise, lock_expires_at, payment_deadline, price_snapshot JSONB, contact_email, contact_phone (copied from the leader at confirm), invoice_no (TT/2026-27/000123, set at confirm), version, confirmed_at |
| booking_units | booking_id, unit_ref (seat no), category, assignee_user_id, ticket_price_paise (GST-inclusive, e.g. 25000), ticket_base_paise (21186), ticket_gst_paise (3814), ticket_gst_rate_bp (1800 = 18%; 1200 if ticket ≤ ₹100), fee_paise (2000), fee_gst_paise (360) |

### Money (payment, refund)
| Table | Columns |
|---|---|
| payments | booking_id, payer_user_id, beneficiary_user_id (differs for Cover / Pay For All), amount_paise, idempotency_key, gateway, gateway_order_id, gateway_txn_id, method UPI/CARD/NETBANKING, status PENDING/SUCCESS/FAILED, failure_reason, succeeded_at |
| refunds | payment_id, booking_id, amount_paise, reason TIMER_EXPIRED/MEMBER_REMOVED/SHOW_CANCELLED/LATE_PAYMENT/ADMIN, status INITIATED/SUCCESS/FAILED, attempts, gateway_refund_id, updated_at |
| invoice_counters | financial_year ('2026-27'), next_no. Row locked with `SELECT ... FOR UPDATE` at confirm, so invoice numbers have no gaps (GST rule) |
| webhook_events | provider, gateway_event_id, event_type, payload JSONB, received_at, processed_at |
| idempotency_keys | user_id, idem_key, endpoint, request_hash, response_status, response_body JSONB, expires_at |
| ious (v1.1) | booking_id, debtor_user_id, creditor_user_id, amount_paise, settled_at |

### Tickets (ticket)
| Table | Columns |
|---|---|
| tickets | booking_id, kind GROUP/SEAT, unit_ref (null for GROUP), holder_user_id, qr_token_id UUID, checked_in_at, checked_in_by, revoked_at |

### Platform
| Table | Columns |
|---|---|
| outbox_events | event_id UUID, topic, aggregate_id, payload JSONB, published_at, attempts |
| processed_events | consumer, event_id, processed_at |
| notifications | user_id, type, title, body, read_at |
| audit_logs | actor_id, action, entity_type, entity_id, before JSONB, after JSONB, ip |

### Later phases (designed now, built later - not in MVP migrations)
Everything here plugs into the ADR-007 core (`item_type` + `item_id`, `booking_units`) without changing MVP tables, except the new nullable columns listed.

#### Phase 2 - movies, richer
| Table | Columns |
|---|---|
| cinema_chains | name (PVR INOX, Cinepolis...), slug, logo_url, gstin, support_email, support_phone. Adds `theatres.chain_id` (nullable: single-screen cinemas have no chain) |
| fnb_items | theatre_id, name (Large Salted Popcorn), description, image_url, category POPCORN/BEVERAGE/SNACK/COMBO, is_veg, price_paise (GST-inclusive), gst_rate_bp (500 = 5%), is_available |
| booking_addons | booking_id, item_type FNB, item_id, assignee_user_id (whose share pays for it in Split Pay), qty, unit_price_paise, gst_paise, total_paise, delivery PICKUP/TO_SEAT, status ORDERED/SERVED/CANCELLED. A snapshot: later price changes do not affect it |
| offers | title, description, kind AUTO (applied without a code, e.g. bank offer) / CODE, discount_type FLAT/PERCENT, discount_value, max_discount_paise, min_order_paise, applies_to TICKET/FEE/FNB/TOTAL, conditions JSONB (payment method, card BIN ranges, city_ids, movie_ids, first booking only, min seats), valid_from, valid_to, total_limit, per_user_limit, funded_by PLATFORM/BANK/CINEMA, status DRAFT/ACTIVE/PAUSED/ENDED |
| coupons | offer_id, code (unique, e.g. `MOVIE100` shared, or `TT-8K2P-QX` single-use), assigned_user_id (null = anyone), max_uses, used_count |
| offer_redemptions | offer_id, coupon_id, booking_id, user_id, discount_paise, status RESERVED (at lock) / APPLIED (at confirm) / RELEASED (booking failed). Unique (offer_id, booking_id) |
| reviews | item_type MOVIE, item_id, user_id, booking_id (only people who watched can review = "verified"), rating 1-10, title, body, tags TEXT[] (Great acting, Slow story), status PUBLISHED/HIDDEN/FLAGGED, helpful_count. Unique (user_id, item_type, item_id). `movies.avg_rating` + `rating_count` updated by an event consumer |

Price breakdown with these: `bookings` gains `discount_paise`, `addons_paise`; `booking_units` gains `discount_paise` (each member's share of the discount, so Split Pay stays fair).

#### Phase 3 - bus, train, packages
| Table | Columns |
|---|---|
| saved_travellers | user_id, full_name (as on ID), gender M/F/O, date_of_birth, nationality, id_type AADHAAR/PAN/PASSPORT/DL/VOTER_ID, id_number_enc (AES-GCM encrypted, key in KMS), id_last4 (for display), berth_preference LOWER/MIDDLE/UPPER/SIDE_LOWER/NONE, meal_preference VEG/NON_VEG/NONE, is_self |
| booking_unit_passengers | booking_unit_id (1-1), saved_traveller_id (nullable, reference only), full_name, gender, age (snapshot at booking: editing a saved traveller never changes an issued ticket), id_type, id_last4, berth_preference, allotted_seat (S4/32/LB), unit_status CNF/RAC/WL/CANCELLED, waitlist_no, supplier_passenger_ref |
| trips | public_id, user_id, title (Goa, Dec 2026), start_date, end_date, status PLANNED/ACTIVE/COMPLETED/CANCELLED. Adds `bookings.trip_id` (nullable): a trip groups a train + hotel + cab booking |

Travel bookings also add to `bookings`: `supplier` (IRCTC, RedBus operator...), `supplier_booking_ref` (PNR / operator ticket no.), `cancellation_policy_snapshot JSONB` (refund rules frozen at booking).

## 3. Redis Keys

| Key | Value | TTL | Purpose |
|---|---|---|---|
| `lock:show:{showId}:seat:{seatNo}` | groupId | 300 s; extended to payment window + 60 s | Seat lock, e.g. `lock:show:101:seat:A1 = 12` |
| `seats:show:{showId}` | seat map JSON | 60 s | Cache; deleted on lock/unlock/confirm/cancel |
| `rl:otp:cooldown:{email}` | 1 | 60 s | Resend cooldown (OTP and verification link) |
| `rl:otp:hour:{email}` | counter | 1 h | Max 5 emails per hour |
| `rl:login:email:{email}` / `rl:login:ip:{ip}` | counter | 15 min / 1 min | Login rate limit (10 per email, 20 per IP) |
| `rl:remind:{groupId}:{userId}` | 1 | 60 s | Remind limit |

## 4. File Storage

| File | Path |
|---|---|
| Movie poster | `posters/{movieId}.jpg` |
| Movie banner | `banners/{movieId}.jpg` |
| Profile photo | not stored: `users.avatar_url` points to Google |
| Ticket PDF | `tickets/{bookingPublicId}.pdf` (generated on first download, cached) |

MVP: `backend/uploads/`, served via `/uploads/*` (posters public; ticket PDFs only through the authorised API). Phase 2: S3 + CloudFront, same keys.

## 5. What NOT To Store

- Never store date of birth, gender or ID numbers before Phase 3 (not needed for movies). From Phase 3, only per traveller, ID numbers encrypted, only last 4 digits shown.
- Never store card numbers, CVV, UPI PIN, bank details - the gateway handles them. Store only gateway order/transaction IDs, amount, status.
- Never store passwords or OTPs in plain text (BCrypt / SHA-256 hash).
- Never store seat **locks** in Postgres - they live in Redis. Postgres stores only the final AVAILABLE/BOOKED state.
- Never log full phone numbers, emails, tokens or OTPs (mask them).

## 6. Data Flow Example (4 friends, ₹250 seats)

1. Lock A1-A4 (rows in `show_seats`) → Redis `lock:show:101:seat:A1..A4 = 12` (TTL 300 s); booking LOCKED; `booking_units` with ₹250 + ₹20 + ₹3.60 each; `price_snapshot` saved.
2. Split Pay → booking PAYMENT_PENDING, deadline +10 min, Redis TTL extended.
3. Each member pays → `payments` PENDING (idempotency key unique) → webhook → SUCCESS, `webhook_events` stored.
4. Last payment → one transaction: seats BOOKED (conditional update), booking CONFIRMED, group BOOKED, 4 seat tickets + 1 group ticket, outbox `booking.confirmed`.
5. Outbox relay → Kafka → notification consumer emails QR to all 4.
6. Redis lock keys deleted; seat map cache evicted.

Core tables use `item_type` + `item_id` instead of `show_id`, so bus/train can reuse them later (ADR-007). MVP has only `item_type = SHOW`.
