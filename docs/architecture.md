# Ticktit Architecture

## Goal
BookMyShow is for solo booking. Ticktit is for group booking.
Create group -> pick seats together -> split payment -> book.

## Monorepo
ticktit/
├── backend/   - Spring Boot 3, Java 21, PostgreSQL, Redis, Kafka
├── frontend/  - React + Vite + Tailwind
├── app/       - Mobile (Later)
├── docs/      - Architecture, API, DB
└── .github/   - CI/CD
## High Level Flow
User -> Frontend (React) -> Backend (Spring Boot API)
-> PostgreSQL (Bookings, Shows, Seats)
-> Redis (Seat Lock - 5 min hold)
-> Kafka (Booking Confirmed Event -> Notification / Payment)

## Key Decisions
- **Seat Lock:** Redis with TTL 5 min. If not paid in 5 min, seat auto-released.
- **Group Booking:** Group has leader + members. Leader creates group, members join via code.
- **Split Payment:** Each member pays own share. Booking confirmed only when all paid OR leader pays full.
- **Idempotency:** Booking API uses Idempotency-Key header to avoid double charge.
