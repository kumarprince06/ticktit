# ADR-002: Use PostgreSQL as Primary Database

Status: Accepted | Date: 2026-11-07

## Context
Need to store Users, Bookings, Payments. Must never lose money data, never double-book seat.

## Decision
PostgreSQL.

## Alternatives Considered
- MySQL: Good, but Postgres has better JSONB (for seats), better constraints, better for complex queries.
- MongoDB: Rejected - No strong transactions, easy to double-book seat if two writes at same time. Money needs ACID.

## Consequences
- Positive: ACID transactions guarantee no double booking, Unique constraint on idempotency_key prevents double charge.
- Negative: Need to manage schema migrations (Flyway).
