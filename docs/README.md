# Ticktit Docs - Index (v4, 11 Nov 2026)

Read in this order when building: PRD → UNIQUE-FEATURES → SRS → architecture → db-design / STORAGE → PAYMENT → REFUND → TICKETS-QR → api → ADRs.

## Product Docs (What + Why)
- `PRD.md` - problem, users, personas, journeys, features (MVP / v1.1 / Phase 2), metrics, roadmap
- `UNIQUE-FEATURES.md` - what makes Ticktit different, with MVP/v1.1/Phase 2 tags
- `SRS.md` - exact requirements FR-01 to FR-08, timers and limits, NFRs, edge cases
- `USER-STORIES.md` - stories with acceptance criteria
- `SCENARIOS.md` - S1-S27 happy, sad, edge and v1.1 scenarios (each one becomes a test)

## Technical Docs (How)
- `architecture.md` - modules, state machine, critical flows, Kafka topics, Redis keys, concurrency
- `api.md` - API contract, idempotency, error format
- `db-design.md` - keys, constraints, indexes, migrations
- `STORAGE.md` - every table and column, Redis keys, file storage, what not to store
- `PAYMENT.md` - price model, payment models, async flow, idempotency, failure cases, mock gateway
- `REFUND.md` - refund cases, automatic refund flow, states, examples
- `TICKETS-QR.md` - group and seat QR, signed tokens, check-in, rotating QR, transfer

## Quality and Operations
- `TEST-PLAN.md` - test levels, tools, what must be proven
- `THREAT-MODEL.md` - what can be attacked and how it is mitigated
- `RUNBOOK.md` - run locally, seed data, reset, debug common problems
- `PRIVACY.md` - personal data handled and user rights

## Decisions (Why This How?)
- `adr/` - ADR-001 to ADR-016. Read the ADRs before changing any tech choice.

## History
- `CHANGELOG.md` - what changed from v3 to v4 and why
