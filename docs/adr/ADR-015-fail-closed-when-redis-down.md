# ADR-015: Fail Closed When Redis Is Unavailable

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma | Amends: ADR-003

## Context
v3 said "Redis down → fallback to DB lock" without a design. A half-designed fallback is where double bookings come from: two lock systems that do not know about each other.

## Decision
- If Redis is unreachable, `POST /bookings/lock` and `/payments/split` (which extends locks) return `503 LOCKING_UNAVAILABLE` with "Booking is temporarily unavailable, please try again in a minute."
- Bookings already PAYMENT_PENDING continue: payments and confirm only need Postgres, and confirm's conditional seat update still prevents double booking.
- Browsing, group pages and tickets keep working (seat map falls back to Postgres AVAILABLE/BOOKED without lock info).
- Health check reports Redis DOWN; alert fires.

## Alternatives Considered
- DB-based lock fallback (`SELECT ... FOR UPDATE` + lock table) - rejected for MVP: second locking system, hard to keep consistent with Redis when it comes back.
- Ignore locks when Redis is down - rejected: unfair and causes confirm failures.

## Consequences
- Positive: correctness over availability; simple to reason about.
- Negative: no new locks during a Redis outage. Acceptable for MVP; Phase 2 can use Redis with replication/Sentinel for availability.
