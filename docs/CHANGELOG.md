# Docs Changelog

## v4 - 11 Nov 2026

### Fixed contradictions
| Was (v3) | Now (v4) | Files |
|---|---|---|
| PRD: refunds manual in MVP; REFUND/SRS: automatic | Automatic refunds in MVP | PRD, SRS, REFUND |
| Fee "₹20 per ticket" vs "10% non-refundable"; total = seats × price with no fee | One price model: ticket + ₹20 fee + 18% GST on fee, all in paise | PAYMENT, REFUND, ADR-009 |
| ADR-005: 24h token in cookie; SRS/API: Bearer + refresh; logout client-only | 15-min Bearer access + rotating refresh cookie, server-side logout | ADR-012 (supersedes ADR-005), SRS, api |
| "No account needed to view" but all group APIs needed JWT | Public group preview + quick join (phone OTP, replaced by Google sign-in in ADR-017) | SRS FR-01.4, FR-03.2, api |
| ADR-012 phone OTP sign-in (needs DLT + SMS cost) | Email + password + email OTP on every login, or Google sign-in (no OTP); refresh max 7 days | ADR-017 (supersedes ADR-012), SRS, api, PRD, STORAGE, architecture |
| "Pay for Aman" in persona but not in SRS/API, and only after seats were released | Cover prompt at 1:00 left; Cover a friend (v1.1); Pay For All pays remaining | PRD, SRS FR-05.7/05.11, PAYMENT |
| Family: seats ≤ members blocked 4 seats for 1 account; "4 tickets in one QR" vs per-user tickets | Pay For All allows seats > members; group QR + seat QR | SRS FR-04.3, TICKETS-QR |
| ADR-003 "no cron" but refunds need a trigger | Deadlines in DB + scheduler; Redis TTL only frees seats | ADR-013 |
| README index missing several files | Full index | README |

### Fixed design gaps
- One 5-min timer → 5-min lock + 10-min payment window, lock extended while paying (ADR-013).
- Seat LOCKED stored in both Postgres and Redis → Postgres stores only AVAILABLE/BOOKED (STORAGE, db-design).
- Ineffective "unique when BOOKED" constraint → conditional UPDATE with row-count check on confirm (architecture 4.3).
- One idempotency key per member blocked retries → one key per attempt + request hash (ADR-010).
- Total ÷ members rounding → share = sum of assigned seats (ADR-009).
- Mock failure on "amount 999" (impossible to choose) → test VPAs `success@mock` / `failure@mock` / `timeout@mock` (ADR-011).
- Synchronous mock gateway → async interface with webhooks + reconciliation (ADR-011).
- Kafka published inside the transaction → transactional outbox (ADR-008).
- QR = booking ID → signed tokens, one-time check-in (ADR-016, TICKETS-QR).
- Account lock after 5 failures (423) → rate limits (429) (ADR-012).
- SMS everywhere → email + in-app + WhatsApp links in MVP; SMS after DLT registration (Phase 2).
- No way to add shows → minimal admin endpoints + seed profile (SRS FR-08, RUNBOOK).
- Two overlapping state machines → booking state machine + simple group status (architecture §3).
- Undefined "fallback to DB lock" → fail closed with 503 (ADR-015).

### Added
- Features: fair split by seat, smart WhatsApp nudges, instant alternatives on conflict, group + seat QR with check-in scanner (MVP); claim your seat, cover a friend + IOU, seat handover, rotating QR, ticket transfer (v1.1); group trust score (Phase 2).
- Docs: TICKETS-QR, TEST-PLAN, THREAT-MODEL, RUNBOOK, PRIVACY, CHANGELOG.
- ADRs: 008-016.
- Scenarios S16-S27; user stories US-11 - US-19.

## v3 - 08 Nov 2026
Initial complete doc set (PRD, SRS, user stories, scenarios, architecture, API, DB, storage, payment, refund, ADR-001 - ADR-007).
