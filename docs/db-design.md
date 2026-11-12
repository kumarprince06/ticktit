# Ticktit - DB Design - v4

PostgreSQL 16. Full column list in STORAGE.md. This file lists the **keys, constraints and indexes that protect the business rules**.

## Conventions
- `id BIGINT GENERATED ALWAYS AS IDENTITY` primary keys; public UUIDs (`public_id`) in URLs where IDs are guessable (groups, bookings).
- Money columns end in `_paise`, type `BIGINT`, `CHECK (... >= 0)` (ADR-009).
- Time columns `TIMESTAMPTZ` in UTC.
- Status columns `VARCHAR` with a `CHECK (status IN (...))`, except `users.role`, `users.status` and `verification_codes.purpose`, which take their values from Java enums only.
- Core tables (`groups`, `bookings`) reference what is booked with `item_type` + `item_id`, no foreign key; the vertical module validates the item (ADR-007). `CHECK (item_type IN ('SHOW'))` for MVP.

## ER Summary
- User 1-N Group (as leader); Group 1-N GroupMember; Group 1-N Booking (attempts), at most 1 active and at most 1 CONFIRMED.
- Booking 1-N BookingUnit (seat + assignee + price); Booking 1-N Payment; Payment 0-1 Refund; Booking 1-N Ticket.
- User 1-N VerificationCode (email link, login OTP, password reset); User 1-N RefreshToken (grouped by family = one signed-in device); User 1-N LoginEvent; User 1-N UserConsent (LoginEvent and UserConsent come in a later migration).
- City 1-N Theatre 1-N Screen 1-N ScreenSeat (layout).
- Movie 1-N Show; Screen 1-N Show; Show 1-N ShowPrice (per category); Show 1-N ShowSeat (one per active ScreenSeat).

## Key Constraints

```sql
-- Identity (ADR-017)
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
-- phone is optional contact info only (no phone login), so it is NOT unique
CREATE UNIQUE INDEX ux_users_google ON users (google_sub) WHERE google_sub IS NOT NULL;
-- status: no CHECK on purpose; the Java UserStatus enum is the only list. ACTIVE and DELETED are used by the checks below, never rename them
-- Every live account has a way to sign in
ALTER TABLE users ADD CONSTRAINT ck_user_login_method
  CHECK (status = 'DELETED' OR password_hash IS NOT NULL OR google_sub IS NOT NULL);
-- ACTIVE means the email is proven (verification link or Google)
ALTER TABLE users ADD CONSTRAINT ck_user_active_verified
  CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL);
ALTER TABLE users ADD CONSTRAINT ck_user_phone CHECK (phone ~ '^\+91[6-9][0-9]{9}$');
-- role: no CHECK on purpose; the Java Role enum is the only list, so adding a role needs no migration
-- verification_codes.purpose: no CHECK on purpose; the Java CodePurpose enum is the only list
CREATE UNIQUE INDEX ux_refresh_hash ON refresh_tokens (token_hash);
-- A token's lifetime is its family's: family_expires_at = sign-in + 7 days, never extended by rotation

-- Catalog
CREATE UNIQUE INDEX ux_city_slug ON cities (slug);
CREATE UNIQUE INDEX ux_theatre_slug ON theatres (city_id, slug);
ALTER TABLE screens ADD CONSTRAINT ux_screen UNIQUE (theatre_id, name);
ALTER TABLE screen_seats ADD CONSTRAINT ux_screen_seat UNIQUE (screen_id, seat_no);
ALTER TABLE screen_seats ADD CONSTRAINT ux_screen_grid UNIQUE (screen_id, grid_row, grid_col);
CREATE UNIQUE INDEX ux_movie_slug ON movies (slug);
ALTER TABLE movies ADD CONSTRAINT ck_cert CHECK (certification IN ('U','UA7','UA13','UA16','A'));
ALTER TABLE shows ADD CONSTRAINT ck_show_time CHECK (ends_at > starts_at AND booking_opens_at <= starts_at);
-- No two shows overlap on one screen
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE shows ADD CONSTRAINT ex_screen_overlap
  EXCLUDE USING gist (screen_id WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (status = 'SCHEDULED');
ALTER TABLE show_prices ADD CONSTRAINT ux_show_price UNIQUE (show_id, category);

-- Groups
CREATE UNIQUE INDEX ux_groups_code ON groups (code);
ALTER TABLE group_members ADD CONSTRAINT ux_group_member UNIQUE (group_id, user_id);

-- Show seats: one row per show per seat; LOCKED lives in Redis, never here
ALTER TABLE show_seats ADD CONSTRAINT ux_show_seat UNIQUE (show_id, seat_no);
ALTER TABLE show_seats ADD CONSTRAINT ck_seat_status CHECK (status IN ('AVAILABLE','BOOKED','BLOCKED'));
ALTER TABLE show_seats ADD CONSTRAINT ck_seat_booked CHECK ((status = 'BOOKED') = (booking_id IS NOT NULL));
-- Confirm uses: UPDATE show_seats SET status='BOOKED', booking_id=? WHERE show_id=? AND seat_no IN (...) AND status='AVAILABLE'
-- and requires rows updated = seat count (architecture.md 4.3). This conditional update is the real double-booking guard.

-- Bookings
CREATE UNIQUE INDEX ux_booking_code ON bookings (booking_code);
CREATE UNIQUE INDEX ux_booking_invoice ON bookings (invoice_no) WHERE invoice_no IS NOT NULL;
CREATE UNIQUE INDEX ux_booking_confirmed_per_group ON bookings (group_id) WHERE status = 'CONFIRMED';
CREATE UNIQUE INDEX ux_booking_active_per_group   ON bookings (group_id) WHERE status IN ('LOCKED','PAYMENT_PENDING');
ALTER TABLE booking_units ADD CONSTRAINT ux_booking_unit UNIQUE (booking_id, unit_ref);
ALTER TABLE booking_units ADD CONSTRAINT ck_unit_tax CHECK (ticket_base_paise + ticket_gst_paise = ticket_price_paise);
ALTER TABLE invoice_counters ADD CONSTRAINT ux_invoice_fy UNIQUE (financial_year);

-- Payments (ADR-010, ADR-011)
ALTER TABLE payments ADD CONSTRAINT ux_pay_idem   UNIQUE (payer_user_id, idempotency_key);
ALTER TABLE payments ADD CONSTRAINT ux_pay_order  UNIQUE (gateway_order_id);
CREATE UNIQUE INDEX ux_pay_txn ON payments (gateway_txn_id) WHERE gateway_txn_id IS NOT NULL;
ALTER TABLE webhook_events ADD CONSTRAINT ux_webhook UNIQUE (provider, gateway_event_id);
ALTER TABLE idempotency_keys ADD CONSTRAINT pk_idem PRIMARY KEY (user_id, idem_key);

-- Refunds: one refund per payment in MVP (no partial refunds yet)
ALTER TABLE refunds ADD CONSTRAINT ux_refund_payment UNIQUE (payment_id);
ALTER TABLE refunds ADD CONSTRAINT ck_refund_amount CHECK (amount_paise > 0);

-- Tickets
ALTER TABLE tickets ADD CONSTRAINT ux_ticket_token UNIQUE (qr_token_id);

-- Events
ALTER TABLE processed_events ADD CONSTRAINT pk_processed PRIMARY KEY (consumer, event_id);
```

## Indexes for Hot Queries

| Query | Index |
|---|---|
| Group by code (join, preview) | `ux_groups_code` |
| Login by email / Google | `ux_users_email`, `ux_users_google` |
| Refresh token lookup | `ux_refresh_hash` |
| My signed-in devices | `refresh_tokens (user_id) WHERE used_at IS NULL AND family_expires_at > now()` |
| Login history / abuse check | `login_events (user_id, created_at)`, `login_events (ip, created_at)` |
| Cleanup unverified users | `users (created_at) WHERE status = 'PENDING_VERIFICATION'` |
| Seat map for a show | `ux_show_seat (show_id, seat_no)` |
| Shows for movie + city + date | `shows (movie_id, starts_at)`, `shows (screen_id, starts_at)`, `screens (theatre_id)`, `theatres (city_id)` |
| Now-showing movies | `movies (status, release_date)` |
| Ticket lookup by code | `ux_booking_code` |
| Scheduler: expired locks | `bookings (lock_expires_at) WHERE status = 'LOCKED'` |
| Scheduler: passed deadlines | `bookings (payment_deadline) WHERE status = 'PAYMENT_PENDING'` |
| Members of a group | `group_members (group_id)` |
| My bookings | `group_members (user_id)` + `bookings (group_id)` |
| Outbox relay | `outbox_events (id) WHERE published_at IS NULL` |
| Stuck payments / refunds | `payments (status, created_at)`, `refunds (status, updated_at)` |

## Migrations (Flyway)
`V1__identity.sql` (users, refresh_tokens, login_events, user_consents) → `V2__catalog_movie.sql` (cities, theatres, screens, screen_seats, movies, shows, show_prices, show_seats) → `V3__groups_bookings.sql` → `V4__payments_refunds.sql` → `V5__tickets.sql` → `V6__platform_outbox_audit.sql`. Seed data is loaded by a separate seed command (RUNBOOK.md), not a migration. Never edit an applied migration; add a new one.
