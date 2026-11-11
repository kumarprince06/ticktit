# Ticktit - DB Design - v4

PostgreSQL 16. Full column list in STORAGE.md. This file lists the **keys, constraints and indexes that protect the business rules**.

## Conventions
- `id BIGINT GENERATED ALWAYS AS IDENTITY` primary keys; public UUIDs (`public_id`) in URLs where IDs are guessable (groups, bookings).
- Money columns end in `_paise`, type `BIGINT`, `CHECK (... >= 0)` (ADR-009).
- Time columns `TIMESTAMPTZ` in UTC.
- Status columns `VARCHAR` with a `CHECK (status IN (...))`.
- Core tables (`groups`, `bookings`) reference what is booked with `item_type` + `item_id`, no foreign key; the vertical module validates the item (ADR-007). `CHECK (item_type IN ('SHOW'))` for MVP.

## ER Summary
- User 1-N Group (as leader); Group 1-N GroupMember; Group 1-N Booking (attempts), at most 1 active and at most 1 CONFIRMED.
- Booking 1-N BookingUnit (seat + assignee + price); Booking 1-N Payment; Payment 0-1 Refund; Booking 1-N Ticket.
- Movie 1-N Show; Theatre 1-N Show; Show 1-N ShowPrice (per category); Show 1-N Seat.

## Key Constraints

```sql
-- Identity
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE UNIQUE INDEX ux_users_phone ON users (phone);

-- Groups
CREATE UNIQUE INDEX ux_groups_code ON groups (code);
ALTER TABLE group_members ADD CONSTRAINT ux_group_member UNIQUE (group_id, user_id);

-- Seats: one physical seat row per show; status only AVAILABLE/BOOKED (LOCKED lives in Redis)
ALTER TABLE seats ADD CONSTRAINT ux_seat UNIQUE (show_id, seat_no);
ALTER TABLE seats ADD CONSTRAINT ck_seat_status CHECK (status IN ('AVAILABLE','BOOKED'));
ALTER TABLE seats ADD CONSTRAINT ck_seat_booked CHECK ((status = 'BOOKED') = (booking_id IS NOT NULL));
-- Confirm uses: UPDATE seats SET status='BOOKED', booking_id=? WHERE show_id=? AND seat_no IN (...) AND status='AVAILABLE'
-- and requires rows updated = seat count (architecture.md 4.3). This conditional update is the real double-booking guard.

-- Bookings
CREATE UNIQUE INDEX ux_booking_confirmed_per_group ON bookings (group_id) WHERE status = 'CONFIRMED';
CREATE UNIQUE INDEX ux_booking_active_per_group   ON bookings (group_id) WHERE status IN ('LOCKED','PAYMENT_PENDING');
ALTER TABLE booking_units ADD CONSTRAINT ux_booking_unit UNIQUE (booking_id, unit_ref);

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
| Seat map for a show | `ux_seat (show_id, seat_no)` |
| Shows for movie + city + date | `shows (movie_id, starts_at)`, `theatres (city)` |
| Scheduler: expired locks | `bookings (lock_expires_at) WHERE status = 'LOCKED'` |
| Scheduler: passed deadlines | `bookings (payment_deadline) WHERE status = 'PAYMENT_PENDING'` |
| Members of a group | `group_members (group_id)` |
| My bookings | `group_members (user_id)` + `bookings (group_id)` |
| Outbox relay | `outbox_events (id) WHERE published_at IS NULL` |
| Stuck payments / refunds | `payments (status, created_at)`, `refunds (status, updated_at)` |

## Migrations (Flyway)
`V1__identity.sql` → `V2__catalog_movie.sql` → `V3__groups_bookings.sql` → `V4__payments_refunds.sql` → `V5__tickets.sql` → `V6__platform_outbox_audit.sql`. Seed data is loaded by a separate seed command (RUNBOOK.md), not a migration. Never edit an applied migration; add a new one.
