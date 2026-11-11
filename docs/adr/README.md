# ADRs - Architecture Decision Records

| ADR | Decision | Status |
|---|---|---|
| ADR-001 | Monorepo | Accepted |
| ADR-002 | PostgreSQL as primary database | Accepted |
| ADR-003 | Redis seat lock with TTL | Accepted, amended by ADR-013, ADR-015 |
| ADR-004 | Kafka for booking events / notifications | Accepted, amended by ADR-008 |
| ADR-005 | JWT auth (24h token) | **Superseded by ADR-012** |
| ADR-006 | Spring Boot 3 + Java 21 | Accepted |
| ADR-007 | Generic booking core, vertical-specific inventory | Accepted |
| ADR-008 | Transactional outbox for Kafka events | Accepted |
| ADR-009 | Money in paise + per-seat price model | Accepted |
| ADR-010 | Idempotency keys per attempt | Accepted |
| ADR-011 | Async payment gateway interface (mock now, Razorpay later) | Accepted |
| ADR-012 | Short access token + rotating refresh cookie + phone OTP | **Superseded by ADR-017** |
| ADR-013 | Deadlines in Postgres, enforced by a scheduler | Accepted |
| ADR-014 | Polling now, WebSocket later | Accepted |
| ADR-015 | Fail closed when Redis is down | Accepted |
| ADR-016 | Signed QR tickets (group + seat), one-time check-in | Accepted |
| ADR-017 | Email + password + email OTP, or Google sign-in; 15-min access, 7-day max refresh | Accepted |
| (future) | Move to microservices one service at a time | To be proposed when Phase 2 starts |

## How to add a new ADR
1. Copy the format: Title, Status, Date, Author, Context, Decision, Alternatives Considered, Consequences.
2. Number = next free number (ADR-018 ...).
3. Status: Proposed → Accepted / Rejected.
4. Never delete or rewrite an accepted ADR. If the decision changes, write a new ADR that **supersedes** or **amends** it, and update only the old ADR's Status line (plus an "Amendments" note).
