# ADR-004: Use Kafka for Booking Events / Notifications

Status: Accepted | Date: 2026-11-07

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
