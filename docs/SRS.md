# Ticktit - Software Requirements Specification (SRS) - v3 Final
Version: 3.0 | IEEE 830 | Author: Prince Kumar Sharma

## 1. Introduction
Purpose: Single source of truth for Developer + Tester. If PRD says what, SRS says exactly how software must behave including errors.
Scope: Web App MVP. Backend Spring Boot 3, Java 21. Frontend React. DB PostgreSQL. Cache Redis. Queue Kafka.

## 2. Functional Requirements

### FR-01 Auth
FR-01.1 Register: Name min 2 chars, Email valid + unique, Phone 10 digit Indian, Password min 8, 1 upper, 1 number, 1 special.
FR-01.2 If email exists -> Error 409: "Email already exists. Please login."
FR-01.3 Login: Email + Password -> JWT 24h + Refresh Token. POST /auth/refresh issues a new JWT.
FR-01.4 Wrong password 5 times -> Lock 15 min -> Error 423: "Account locked. Try after 15 min."
FR-01.5 Logout -> Invalidate token client side.
FR-01.6 All APIs except /auth/* and GET /movies* require JWT (Authorization: Bearer). If no JWT -> 401 Unauthorized.

### FR-02 Movies and Shows
FR-02.1 GET /movies?city=Siliguri&language=Hindi&genre=Action -> List, paginated 20 per page.
FR-02.2 GET /movies/{id} -> Poster, Title, Duration, Rating, Synopsis, Cast.
FR-02.3 GET /shows?movieId=&city=&date= -> List shows.
FR-02.4 Show fields: Theatre Name, Screen, Time, Price, Available Seats Count, Total Seats.
FR-02.5 If no shows -> Empty list + Message: "No shows for this date."

### FR-03 Group
FR-03.1 POST /groups {showId} -> Creates group, generates 6-char code (A-Z, 0-9, no O/0, I/1). Leader = creator. Status OPEN.
FR-03.2 Max members 8. If 9th tries -> Error 400: "Group is full (max 8)."
FR-03.3 POST /groups/{code}/join -> Join. If group BOOKED -> Error 409: "Group already booked."
FR-03.4 If group CANCELLED -> Error 409: "Group cancelled."
FR-03.5 DELETE /groups/{id}/members/{userId} -> Leader can remove member before CONFIRMED. If removed member already PAID -> Auto-refund + Notification.
FR-03.6 DELETE /groups/{id} -> Leader can delete group if no payment yet. Status -> CANCELLED.
FR-03.7 GET /groups/{id} -> Members list with status JOINED, PAID, PENDING.

### FR-04 Seat Lock
FR-04.1 GET /shows/{id}/seats -> Seat Map: seatNo, status AVAILABLE/LOCKED/BOOKED, lockedUntil.
FR-04.2 Only Leader can select seats. Members can view only (MVP rule).
FR-04.3 Selected seats count <= Members count. If Leader selects 5 seats for 4 members -> Error 400: "You can select max 4 seats."
FR-04.4 POST /bookings/lock {groupId, seats:[A1,A2]} -> Locks in Redis for 300 sec. Returns bookingId + lockedUntil.
FR-04.5 If any seat already LOCKED/BOOKED -> Error 409: "Seats [A1] already locked. Please select others." No partial lock, all or nothing.
FR-04.6 Timer: UI shows countdown. Backend Redis TTL expiry -> Seat auto AVAILABLE. No cron needed.
FR-04.7 Leader can unlock manually before expiry -> POST /bookings/unlock.

### FR-05 Payment and Booking
FR-05.1 Total = seats * price. Share = Total / members. Example 4 * 250 = 1000, Share = 250.
FR-05.2 POST /payments/split {groupId} -> Creates payment intents per member.
FR-05.3 POST /payments/pay {groupId} + Header Idempotency-Key. User comes from JWT, amount is calculated server-side. If same key repeats -> Return previous result, do NOT charge again.
FR-05.4 Payment methods: UPI, Card (Mock gateway for MVP - always success except amount 999 for testing failure).
FR-05.5 If ALL paid -> Booking CONFIRMED, Seats BOOKED, Kafka event booking.confirmed published.
FR-05.6 If SOME paid and timer expires -> Booking CANCELLED, Paid members refunded, Seats released. See REFUND.md.
FR-05.7 POST /payments/pay-for-all {groupId} -> Leader option: Pay For All -> Leader pays total, all members marked PAID.
FR-05.8 On confirm, generate QR Code (Booking ID encoded) + Ticket PDF link.
FR-05.9 GET /bookings/{id} -> Booking detail: Movie, Theatre, Time, Seats, Members, Payment status per member.

### FR-06 My Bookings and Notifications
FR-06.1 GET /bookings/my -> List upcoming + past, sorted by date desc.
FR-06.2 On booking confirm: SMS + Email to all members (Kafka consumer).
FR-06.3 On payment success: SMS to that member: "Payment Rs 250 successful."
FR-06.4 On timer expire: SMS to Leader: "Your lock expired."

## 3. Non-Functional Requirements
NFR-01 Performance: Seat map < 1 sec, API p95 < 300ms, Lock API < 100ms.
NFR-02 Concurrency: 100 concurrent lock requests, only 1 wins per seat.
NFR-03 Security: BCrypt password, JWT via Authorization Bearer header, No password in logs, SQL Injection prevented via JPA.
NFR-04 Reliability: No double booking, No double charge - 0 tolerance.
NFR-05 Usability: Mobile responsive, Join via link < 30 sec, No install needed for member view.
NFR-06 Availability: 99% for MVP.

## 4. Edge Cases
EC-01 Two groups lock same seat same second -> Only 1 succeeds, other gets 409.
EC-02 User clicks Pay twice in 1 sec -> Charged once, second returns the same payment result.
EC-03 User closes tab after lock -> Seat auto-releases after 5 min.
EC-04 Leader removes PAID member -> Refund + Seat released.
EC-05 Internet disconnects during payment -> Booking PENDING, retry with same Idempotency-Key succeeds, no double charge.
EC-06 Group full, 9th member tries -> Error 400 Group full.
EC-07 Leader tries to select 5 seats for 4 members -> Error 400.
EC-08 Payment amount 999 -> Mock gateway fails -> Booking stays PENDING, user can retry.

## 5. UI Requirements
Home: Movie list, Search, City selector (Siliguri default).
Movie Detail: Poster, Shows list, Create Group button.
Seat Map: Grid A1-A10, B1-B10, Legend Green/Yellow/Grey, Timer.
Group Page: Members list, Payment status, Pay button, Remind button.
Success: QR, Download Ticket, Share button.
