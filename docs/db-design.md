# Ticktit - DB Design - v3 Final
See STORAGE.md for full tables. Key constraints:
- users.email UNIQUE
- groups.code UNIQUE
- payments.idempotency_key UNIQUE
- seats (show_id, seat_no) UNIQUE when BOOKED
- bookings: group_id UNIQUE when CONFIRMED (one booking per group)
ER: User 1-N Group (as Leader), Group 1-N GroupMembers, Group 1-1 Booking, Booking 1-N Payments, Booking 1-N Tickets, Show 1-N Seats, Movie 1-N Shows, Theatre 1-N Shows.
Indexes: groups.code, payments.idempotency_key, seats.show_id, bookings.group_id, group_members.group_id.
Migrations: Flyway V1__init.sql creates all tables.
- Core tables use item_type + item_id (not show_id); validated by the vertical module, no DB foreign key (ADR-007).
