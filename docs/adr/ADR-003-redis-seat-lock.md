# ADR-003: Use Redis for Seat Lock with TTL

Status: Accepted (amended by ADR-013 and ADR-015 on 2026-11-11) | Date: 2026-11-07

## Context
When user locks seats, we must hold them for 5 min, then auto-release if not paid. If we use DB only, we need a cron job to clean expired locks every minute - slow and complex.

## Decision
Redis with TTL (Time To Live) = 300 seconds (5 min). Key: `lock:show:{showId}:seat:{seatNo}`

## Alternatives Considered
- DB + Cron Job: Rejected - Cron runs every 1 min, seat may stay locked 6 min, bad UX, extra load on DB.
- In-memory HashMap: Rejected - If server restarts, all locks lost.

## Consequences
- Positive: Auto-expiry, no cron, < 10ms lock check, survives high concurrency.
- Negative: Redis is extra infra to maintain. If Redis down, locking fails - need fallback to DB.

## Amendments (2026-11-11)
- ADR-013: Redis TTL frees the seats, but booking expiry, cancellation and refunds are driven by deadlines stored in Postgres and a scheduler. Redis keyspace notifications are not reliable enough to trigger money actions.
- ADR-015: if Redis is down, locking fails closed (503). There is no database fallback lock in MVP.
- The final double-booking guard is the conditional `UPDATE seats ... WHERE status = 'AVAILABLE'` on confirm (architecture.md 4.3).
