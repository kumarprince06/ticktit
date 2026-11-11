# ADR-004: Use Kafka for Booking Events / Notifications

Status: Accepted (amended by ADR-008 on 2026-11-11) | Date: 2026-11-07

## Context
On booking confirm, we need to: Send SMS, Send Email, Update Analytics. If we do all in same API request, user waits 3-5 sec.

## Decision
Backend publishes `booking.confirmed` event to Kafka. Separate consumers handle SMS, Email.

## Alternatives Considered
- Direct call (Synchronous): Rejected - If SMS service down, booking fails even though payment succeeded. Bad.
- No queue, just DB: Rejected - User waits, no retry if SMS fails.

## Consequences
- Positive: Booking API fast (<300ms), SMS/Email retry automatically, easy to add new consumer later (e.g. WhatsApp).
- Negative: Kafka is heavy for MVP. For MVP we can use Kafka in Docker, or replace with simple in-memory queue if needed, but interface stays same.

## Amendments (2026-11-11)
- ADR-008: events are written to an outbox table in the same transaction as the business change and relayed to Kafka. Publishing directly inside `@Transactional` could lose events or send events for rolled-back changes.
- Topics in v4: payment.succeeded, booking.confirmed, booking.failed, booking.cancelled, refund.requested, refund.completed, group.reminder (architecture.md section 5).
