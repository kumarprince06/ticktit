# ADR-014: Polling for Live Updates in MVP, WebSocket Later

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma

## Context
The live collaborative seat map and payment dashboard need near-real-time updates for up to 8 people per group. WebSocket adds connection management, auth on connect, and cross-instance fan-out.

## Decision
- MVP: the UI polls `GET /shows/{id}/seats` and `GET /groups/{id}` every 2 s while the page is visible (stop when the tab is hidden).
- Responses support `ETag` / `If-None-Match` so unchanged data returns `304` cheaply; the seat map is cached in Redis for 60 s and evicted on change.
- Phase 2: Spring WebSocket (STOMP), topic `/topic/groups/{id}`, JWT checked on connect, membership checked on subscribe; Kafka events fan out to every instance.

## Alternatives Considered
- WebSocket in MVP - rejected: more moving parts while the core money flow is still being built.
- Server-Sent Events - good option for Phase 2 too; STOMP chosen because it is common in Spring jobs.

## Consequences
- Positive: simple, stateless, easy to test and debug.
- Negative: up to 2 s delay; more requests (8 members × 2 endpoints / 2 s ≈ 8 req/s per active group), fine for MVP scale.
