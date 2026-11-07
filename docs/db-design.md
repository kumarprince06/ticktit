# Ticktit DB Design - PostgreSQL

## Tables

### users
- id UUID PK, name, email UNIQUE, phone, password_hash, created_at

### movies
- id UUID PK, title, language, genre, duration_min, poster_url, release_date

### theatres
- id UUID PK, name, city, address, lat, lng

### shows
- id UUID PK, movie_id FK, theatre_id FK, screen, start_time, price, total_seats

### seats
- id UUID PK, show_id FK, seat_no (A1, A2...), status (AVAILABLE, LOCKED, BOOKED), locked_by, locked_until

### groups
- id UUID PK, code UNIQUE (6-char), leader_id FK, show_id FK, status (OPEN, LOCKED, BOOKED)

### group_members
- id UUID PK, group_id FK, user_id FK, status (JOINED, PAID)

### bookings
- id UUID PK, group_id FK, show_id FK, user_id FK, seats JSONB, total_amount, status (PENDING, CONFIRMED, CANCELLED), idempotency_key UNIQUE

### payments
- id UUID PK, booking_id FK, user_id FK, amount, method (UPI, CARD), status, gateway_txn_id
