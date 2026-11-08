# ADR-007: Generic Booking Core, Vertical-Specific Inventory

Status: Accepted | Date: 2026-11-08 | Author: Prince Kumar Sharma

## Context
Ticktit starts with movies, but the roadmap adds theatre / live shows, bus, train and travel packages (see PRD.md section 11). Every vertical has the same flow: create group -> hold limited inventory -> each member pays a share -> confirm or refund. Only the inventory differs (cinema seat, bus seat, train berth, hotel room). If the booking core is hard-wired to `show_id`, each new vertical means rewriting groups, bookings, payments and refunds.

## Decision
Split the backend into a shared core and one module per vertical.

- Core (built once, vertical-agnostic): Group, GroupMember, Booking, Payment, Refund, Ticket, Notification, Hold timer.
- Vertical module (one per vertical): its own catalogue and inventory tables, e.g. Movie module = movies, theatres, shows, seats. Bus module later = routes, trips, partner API client.
- Core tables refer to what is booked with `item_type` + `item_id` instead of `show_id`. MVP has one value: `item_type = SHOW`.
- Each vertical implements one interface the core calls: `hold(itemId, units, groupId)`, `release(holdId)`, `confirm(holdId)`, `price(itemId, units)`.
- Rule: core code never imports a vertical module. Verticals may import core.

Only the movie/theatre vertical is built now. No bus, train or package code until that phase starts.

## Alternatives Considered
- Keep `show_id` everywhere, refactor later - Rejected: cheap today, but by Phase 2 bookings and payments hold real money data and the migration touches every core table and service.
- Separate app per vertical - Rejected: duplicates split pay, idempotency and refunds, the hardest and most valuable parts.
- Full plugin system / microservice per vertical now - Rejected: over-engineered for a solo MVP with one vertical.

## Consequences
- Positive: Adding a vertical = new module + implement the hold/release/confirm/price interface. Split pay and refunds are reused untouched. Packages (Phase 4) become a booking with several items.
- Negative: `item_type` + `item_id` cannot be a DB foreign key; the core must validate the item through the vertical module. Slightly more indirection in MVP.
- For bus and train, the partner's system owns the inventory, so `hold` calls their API and Redis only mirrors the hold. ADR-003 (Redis seat lock) applies to inventory we own (movies, theatre).
