# ADR-013: Deadlines Stored in Postgres, Enforced by a Scheduler

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma | Amends: ADR-003

## Context
ADR-003 said "Redis TTL expiry → seat available, no cron needed". That is true for freeing seats, but when the payment window ends we must also cancel the booking, refund paid members and notify everyone. Redis keyspace expiry notifications are fire-and-forget (lost if no subscriber is connected), so they cannot drive money actions. Also, v3 used one 5-minute timer for both choosing seats and collecting payment from up to 8 people, which is too short and caused "paid at 4:59, seat gone at 5:00" bugs.

## Decision
- Two timers: `lock.ttl` = 5 min (LOCKED, leader assigns and starts payment) and `payment.window` = 10 min (PAYMENT_PENDING). Both are config values.
- Deadlines are columns: `bookings.lock_expires_at`, `bookings.payment_deadline`.
- When payment starts, Redis lock keys are extended to `payment_deadline + 60 s` so seats cannot be stolen while money is moving.
- A scheduler runs every 15 s: LOCKED past `lock_expires_at` → RELEASED; PAYMENT_PENDING past `payment_deadline` → FAILED + refunds (same transaction writes outbox events). Uses `FOR UPDATE SKIP LOCKED`; with more than one instance, ShedLock ensures one runner.
- Nudge at 3 min left and cover prompt at 1 min left are triggered by the same scheduler.

## Alternatives Considered
- Redis keyspace notifications - rejected: not guaranteed delivery.
- One 5-min timer for everything - rejected: too short for group payments.
- Delayed Kafka messages - rejected: Kafka has no native delayed delivery; more complexity.

## Consequences
- Positive: reliable expiry and refunds even after restarts; timers are visible and testable in the DB.
- Negative: up to 15 s delay after a deadline (acceptable; the UI shows 0:00 and "finishing up").
