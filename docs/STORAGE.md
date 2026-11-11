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

### Identity (core.auth)
| Table | Columns |
|---|---|
| users | public_id UUID, name, email, phone (optional), password_hash (BCrypt, null for Google-only users), google_sub (unique, nullable), email_verified_at, roles (USER/ADMIN/SCANNER), status ACTIVE/BLOCKED, updated_at |
| refresh_tokens | user_id, family_id UUID, token_hash, expires_at, revoked_at, replaced_by_id |

### Catalog (vertical.movie)
| Table | Columns |
|---|---|
| movies | title, poster_url, duration_min, rating, synopsis, languages TEXT[], genres TEXT[], cast JSONB |
| theatres | name, city, address |
| shows | movie_id, theatre_id, screen, starts_at, ends_at, status SCHEDULED/CANCELLED, total_seats |
| show_prices | show_id, category CLASSIC/PRIME/RECLINER, price_paise (GST-inclusive ticket price) |
| seats | show_id, seat_no (A1), row_label, col_no, category, status AVAILABLE/BOOKED, booking_id, version |

### Groups and bookings (core)
| Table | Columns |
|---|---|
| groups | public_id, code (6-char unique), item_type SHOW, item_id, leader_id, status OPEN/BOOKED/CANCELLED, max_members 8 |
| group_members | group_id, user_id, role LEADER/MEMBER, status JOINED/PAID/PENDING, joined_at |
| bookings | public_id, group_id, item_type, item_id, status LOCKED/PAYMENT_PENDING/CONFIRMED/RELEASED/FAILED/CANCELLED, mode SPLIT/PAY_FOR_ALL (null until payment starts), total_paise, paid_paise, lock_expires_at, payment_deadline, price_snapshot JSONB, version, confirmed_at |
| booking_units | booking_id, unit_ref (seat no), category, assignee_user_id, ticket_price_paise, fee_paise (2000), fee_gst_paise (360) |

### Money (core.payment, core.refund)
| Table | Columns |
|---|---|
| payments | booking_id, payer_user_id, beneficiary_user_id (differs for Cover / Pay For All), amount_paise, idempotency_key, gateway, gateway_order_id, gateway_txn_id, method UPI/CARD/NETBANKING, status PENDING/SUCCESS/FAILED, failure_reason, succeeded_at |
| refunds | payment_id, booking_id, amount_paise, reason TIMER_EXPIRED/MEMBER_REMOVED/SHOW_CANCELLED/LATE_PAYMENT/ADMIN, status INITIATED/SUCCESS/FAILED, attempts, gateway_refund_id, updated_at |
| webhook_events | provider, gateway_event_id, event_type, payload JSONB, received_at, processed_at |
| idempotency_keys | user_id, idem_key, endpoint, request_hash, response_status, response_body JSONB, expires_at |
| ious (v1.1) | booking_id, debtor_user_id, creditor_user_id, amount_paise, settled_at |

### Tickets (core.ticket)
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

## 3. Redis Keys

| Key | Value | TTL | Purpose |
|---|---|---|---|
| `lock:show:{showId}:seat:{seatNo}` | groupId | 300 s; extended to payment window + 60 s | Seat lock, e.g. `lock:show:101:seat:A1 = 12` |
| `seats:show:{showId}` | seat map JSON | 60 s | Cache; deleted on lock/unlock/confirm/cancel |
| `otp:{challengeId}` | OTP hash + user id + attempt count | 300 s | Email OTP (login / register) |
| `otp:cooldown:{email}` | 1 | 60 s | Resend cooldown |
| `rl:login:acct:{email}` / `rl:login:ip:{ip}` | counter | 15 min | Login rate limit |
| `rl:remind:{groupId}:{userId}` | 1 | 60 s | Remind limit |
| `refresh:revoked:{familyId}` | 1 | 7 days | Revoked refresh family |

## 4. File Storage

| File | Path |
|---|---|
| Movie poster | `posters/{movieId}.jpg` |
| Ticket PDF | `tickets/{bookingPublicId}.pdf` (generated on first download, cached) |

MVP: `backend/uploads/`, served via `/uploads/*` (posters public; ticket PDFs only through the authorised API). Phase 2: S3 + CloudFront, same keys.

## 5. What NOT To Store

- Never store card numbers, CVV, UPI PIN, bank details - the gateway handles them. Store only gateway order/transaction IDs, amount, status.
- Never store passwords or OTPs in plain text (BCrypt / SHA-256 hash).
- Never store seat **locks** in Postgres - they live in Redis. Postgres stores only the final AVAILABLE/BOOKED state.
- Never log full phone numbers, emails, tokens or OTPs (mask them).

## 6. Data Flow Example (4 friends, ₹250 seats)

1. Lock A1-A4 → Redis `lock:show:101:seat:A1..A4 = 12` (TTL 300 s); booking LOCKED; `booking_units` with ₹250 + ₹20 + ₹3.60 each; `price_snapshot` saved.
2. Split Pay → booking PAYMENT_PENDING, deadline +10 min, Redis TTL extended.
3. Each member pays → `payments` PENDING (idempotency key unique) → webhook → SUCCESS, `webhook_events` stored.
4. Last payment → one transaction: seats BOOKED (conditional update), booking CONFIRMED, group BOOKED, 4 seat tickets + 1 group ticket, outbox `booking.confirmed`.
5. Outbox relay → Kafka → notification consumer emails QR to all 4.
6. Redis lock keys deleted; seat map cache evicted.

Core tables use `item_type` + `item_id` instead of `show_id`, so bus/train can reuse them later (ADR-007). MVP has only `item_type = SHOW`.
