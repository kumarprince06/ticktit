# ADR-008: Transactional Outbox for Kafka Events

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma | Amends: ADR-004

## Context
On confirm we update Postgres (booking CONFIRMED, seats BOOKED) and publish `booking.confirmed` to Kafka. These are two systems. If we publish inside `@Transactional` and the transaction rolls back, friends get a "confirmed" email for a booking that does not exist. If we publish after commit and Kafka is down or the app crashes, the event is lost and nobody gets tickets or refunds.

## Decision
- Write events to an `outbox_events` table **in the same transaction** as the business change.
- An outbox relay (`@Scheduled` every 500 ms, batch of 100, `FOR UPDATE SKIP LOCKED`) publishes unpublished rows to Kafka and sets `published_at`.
- Consumers are idempotent: they insert `(consumer, event_id)` into `processed_events` in the same transaction as their work; a duplicate key means "already done".
- Failed consumers retry (1 s, 10 s, 60 s), then send to `<topic>.DLT` for admin replay.

## Alternatives Considered
- Publish directly in the transaction - rejected: phantom events on rollback.
- Publish after commit - rejected: events lost on crash or Kafka outage.
- Debezium CDC - rejected for MVP: extra infra; can replace the relay later with the same table.

## Consequences
- Positive: no lost or phantom events; Kafka can be down without breaking bookings; easy to explain in interviews.
- Negative: up to ~1 s extra delay for emails; one more table and a relay job; at-least-once delivery means consumers must be idempotent.
