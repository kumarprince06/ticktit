# ADR-003: Use Redis for Seat Lock with TTL

Status: Accepted | Date: 2026-11-07

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
