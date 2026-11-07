# Ticktit - Software Requirements Specification (SRS)
Version: 1.0 | IEEE 830 Format

## 1. Introduction
### 1.1 Purpose
This SRS defines exact functional + non-functional requirements for Ticktit MVP. Developer, Tester, and Product will use this as single source of truth.
### 1.2 Scope
Web app only for MVP. Backend: Spring Boot, Frontend: React. DB: PostgreSQL, Cache: Redis, Queue: Kafka.

## 2. Functional Requirements

### FR-01: User Authentication
- FR-01.1: User can register with Name, Email, Phone, Password. Email must be unique.
- FR-01.2: Password min 8 chars, 1 uppercase, 1 number.
- FR-01.3: Login returns JWT token valid for 24h.
- FR-01.4: If wrong password 5 times, account locked for 15 min.
- FR-01.5: Error: "Email already exists" if duplicate.

### FR-02: Browse Movies & Shows
- FR-02.1: User can list movies with filter: City, Language, Genre.
- FR-02.2: Movie detail shows: Poster, Duration, Rating, Synopsis.
- FR-02.3: User can list shows for movie + city + date.
- FR-02.4: Show shows: Theatre name, Screen, Time, Price, Available seats count.

### FR-03: Group Management
- FR-03.1: Logged-in user can create group for a specific Show. System generates 6-char unique code (e.g. TICK12).
- FR-03.2: Group has Leader (creator), Max members = 8.
- FR-03.3: User can join group via code. If group is FULL, error: "Group is full".
- FR-03.4: If group status is BOOKED, no new member can join.
- FR-03.5: Leader can remove member before booking confirmed.
- FR-03.6: Leader can delete group if no payment done yet.

### FR-04: Seat Selection & Locking
- FR-04.1: Seat map shows: AVAILABLE (green), LOCKED (yellow), BOOKED (grey).
- FR-04.2: Only Leader can select seats, Members can view (MVP rule).
- FR-04.3: User can select max seats = number of members in group.
- FR-04.4: On "Lock Seats", system locks seats in Redis for 5 min.
- FR-04.5: If seats already LOCKED by other group, error: "Seats already locked, please select others".
- FR-04.6: If 5 min expires without payment, seats auto-release to AVAILABLE.
- FR-04.7: Lock timer shows countdown in UI: "04:32 left".

### FR-05: Split Payment & Booking
- FR-05.1: Total = Seats * Price. Share = Total / Members.
- FR-05.2: Each member sees "Pay Rs X" button.
- FR-05.3: Member can pay via UPI / Card (Mock gateway for MVP).
- FR-05.4: System uses Idempotency-Key to prevent double charge.
- FR-05.5: If ALL members paid -> Booking status = CONFIRMED, Seats = BOOKED.
- FR-05.6: If SOME members paid and timer expires -> Paid members get auto-refund (mock), Booking = CANCELLED.
- FR-05.7: Leader can choose "Pay for all" option - pays total in one go.
- FR-05.8: On confirm, each member gets Ticket with QR Code.

### FR-06: My Bookings
- FR-06.1: User can see list of past + upcoming bookings.
- FR-06.2: Booking detail shows: Movie, Theatre, Time, Seats, Group members, Payment status.

## 3. Non-Functional Requirements
- NFR-01 Performance: Seat map load < 1 sec. API p95 < 300ms.
- NFR-02 Scalability: Support 100 concurrent users locking seats in MVP.
- NFR-03 Security: Passwords hashed with BCrypt. JWT in HttpOnly. No password in logs.
- NFR-04 Availability: 99% uptime for MVP (single server ok).
- NFR-05 Usability: Mobile responsive. Group join in < 30 sec via link.
- NFR-06 Reliability: No double booking, No double charge - 0 tolerance.

## 4. Edge Cases
- EC-01: Two groups try to lock same seat at same second -> Only 1 succeeds.
- EC-02: User clicks Pay twice quickly -> Charged once.
- EC-03: User closes tab after lock -> Seat auto-releases after 5 min.
- EC-04: Leader removes member after member paid -> Member auto-refunded.
- EC-05: Internet disconnects during payment -> Booking stays PENDING, user can retry with same Idempotency-Key.

## 5. UI Requirements
- Home: Movie list, Search, City selector
- Movie Detail: Poster, Shows list
- Seat Map: Grid, Legend, Timer
- Group Page: Members list, Payment status per member (Paid / Pending)
- Booking Success: QR, Download Ticket
