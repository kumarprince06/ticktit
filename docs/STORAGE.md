# Ticktit - Storage Design - v3 Final

## 1. Overview - 3 Storages, 3 Jobs
PostgreSQL (Main DB): Permanent data Users, Movies, Shows, Groups, Bookings, Payments, Refunds. Must never lose money data. Needs ACID, Transactions, Unique constraints.
Redis (Cache + Lock): Temporary data Seat Locks 5 min, Sessions, Seat Map Cache. Fast <10ms, Auto-expiry via TTL, Handles concurrency.
S3 / Local (File Storage): Files Movie Posters, QR Ticket Images, Ticket PDFs. Files are big, do not store in DB. S3 cheap, scalable. MVP Local folder /uploads, Phase 2 AWS S3.

## 2. PostgreSQL Tables
users: id, name, email UNIQUE, phone, password_hash BCrypt, created_at.
movies: id, title, poster_url, duration, rating, synopsis, language, genre.
theatres: id, name, city, address.
shows: id, movie_id, theatre_id, screen, show_time, price, total_seats.
groups: id, code UNIQUE 6-char, item_type SHOW, item_id, leader_id, status OPEN/BOOKED/CANCELLED, max_members 8.
group_members: id, group_id, user_id, status JOINED/PAID/PENDING, joined_at.
seats: id, show_id, seat_no A1, status AVAILABLE/LOCKED/BOOKED, locked_until, booking_id.
bookings: id, group_id, item_type SHOW, item_id, seats JSON, total_amount, status LOCKED/PENDING/CONFIRMED/CANCELLED, created_at.
payments: id, booking_id, user_id, amount, idempotency_key UNIQUE, gateway_txn_id, status PENDING/SUCCESS/FAILED.
refunds: id, payment_id, amount, reason, status INITIATED/SUCCESS/FAILED.
tickets: id, booking_id, user_id, qr_code_url, pdf_url.

## 3. Redis Keys
Seat Lock: lock:show:{showId}:seat:{seatNo} -> groupId, TTL 300 sec. Example lock:show:101:seat:A1 = group_12.
Seat Map Cache: seats:show:{showId} -> JSON seat map, TTL 60 sec, invalidated on lock/book.
Rate Limit: ratelimit:login:{ip} -> Count failed logins, TTL 15 min.
OTP: otp:{phone} -> OTP code, TTL 5 min.

## 4. File Storage
Movie Posters: posters/{movieId}.jpg. QR Tickets: tickets/{bookingId}/{userId}.png. Ticket PDF: tickets/{bookingId}.pdf. MVP: backend/uploads/, serve via /uploads/* API. Phase 2: AWS S3 + CloudFront, same URLs.

## 5. What NOT To Store
DO NOT store Card number, UPI PIN, CVV, Password in plain text. DO store only gateway_txn_id, amount, status. DO NOT store Seat locks in Postgres only - Must be in Redis for speed.

## 6. Data Flow Example
1. Lock A1 -> Redis lock:show:101:seat:A1 = group_12 TTL 300. 2. Pay -> Postgres payments row (idempotency_key UNIQUE). 3. All paid -> Postgres bookings CONFIRMED, seats BOOKED (Transaction). 4. QR generated -> File Storage tickets/123.png. 5. Kafka event -> SMS + Email with QR link. 6. Redis lock deleted -> Seat BOOKED in Postgres.

Note: Core tables (groups, bookings) use item_type + item_id, not show_id, so bus/train can reuse them later (ADR-007). MVP has only item_type = SHOW, item_id = shows.id.
