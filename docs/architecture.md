# Ticktit - Architecture - v3 Final

## System Overview
Client: React Web App (Mobile Responsive) -> API Gateway / Backend: Spring Boot 3, Java 21 -> DB PostgreSQL, Cache Redis, Queue Kafka, File Storage Local/S3.

## Components
Frontend React: Pages Home, Movie Detail, Seat Map, Group Page, My Bookings, Success. Calls REST APIs, Polling for live seat map (Phase 2 WebSocket).
Backend Spring Boot: Shared core (Auth, Group, Booking, Payment, Refund, Notification) + one module per vertical (MVP: Movie module with Movie, Show, Seat). Core never imports a vertical (ADR-007). Services with @Transactional. Security Spring Security + JWT.
PostgreSQL: Source of truth for money. All bookings/payments in transaction.
Redis: Seat lock with TTL 300s, Seat map cache, Rate limit.
Kafka: Topics booking.confirmed, booking.cancelled, payment.success. Consumers: SMS Consumer, Email Consumer, Refund Consumer. MVP can run Kafka in Docker, or mock with in-memory queue, interface same.

## Booking Flow - Critical Path
1. Leader POST /bookings/lock -> Backend checks Redis, if free sets lock TTL 300, returns lockedUntil. 2. Members POST /payments/pay with Idempotency-Key -> Backend checks DB for duplicate key, calls Mock Gateway, saves payment. 3. If all paid -> @Transactional: Update bookings CONFIRMED, seats BOOKED, Publish Kafka booking.confirmed. 4. Consumers send SMS/Email with QR.

## Concurrency Control
Redis SET NX (only if not exists) ensures only 1 group wins seat. DB Unique Constraint on seats (show_id + seat_no + status BOOKED) as second line of defence. Idempotency-Key UNIQUE in payments prevents double charge.

## Scalability
MVP: Single server, Docker Compose: app + postgres + redis + kafka. 100 concurrent users. Phase 2: Multiple app instances behind Load Balancer, Redis shared, Postgres primary.

## Security
BCrypt passwords, JWT in Authorization Bearer header, No password in logs, Input validation, SQL Injection prevented via JPA.
