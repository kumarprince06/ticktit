# Ticktit - Product Requirements Document (PRD) - v4

Version: 4.0 | Author: Prince Kumar Sharma | Date: 11 Nov 2026 | Previous: v3 (08 Nov 2026) - see CHANGELOG.md

## 1. Executive Summary

Ticktit is a group-first booking platform for India. It starts with movie tickets and grows into theatre / live shows, bus, train and full travel packages - all with the same group + split-pay experience.

Tagline: **"Book Together, Pay Separately, Sit Together."**

- BookMyShow solves: "I want to book a ticket."
- Ticktit solves: "We want to book TOGETHER, sit TOGETHER, and pay SEPARATELY without awkward money chasing."

## 2. Problem Statement

In India, most movie plans are group plans, but ticket apps are built for one buyer.

Real WhatsApp example:

> Prince: "Pushpa 2, Saturday 8 PM, INOX Siliguri, shall we go?"
> Rahul: "Yes, 2 tickets" - Aman: "1 for me too"
> Prince books 4 tickets and pays ₹1,094.40. Next day: "Bro, send me ₹273.60." Aman forgets. Prince loses money + friendship awkwardness.

Pain points:

1. **Money pain** - the leader pays for everyone, then chases money.
2. **Seat pain** - friends cannot see the same seat map ("Which seats did you take?").
3. **Coordination pain** - if one friend drops out, the whole booking gets messed up.
4. **Trust pain** - friends do not know if their seat is really booked.

## 3. Solution

Create Group → Share link on WhatsApp → Friends join in 30 sec (no install) → See the SAME live seat map → Leader locks seats → Each member pays for **their own seat** → Everyone gets their own QR ticket.

## 4. Target Users

| Segment | Who | Typical group |
|---|---|---|
| Primary | College students 18-24, Siliguri, Kolkata, Patna | 3-6 friends |
| Secondary | Families 30-45 | Family of 4, one payer |
| Tertiary | Office colleagues | Team outing of 5-8 |

## 5. Personas

### Persona 1: Prince Kumar Sharma - Planner / Group Leader
Age 21, Siliguri, B.Tech student + developer. Android, UPI (PhonePe), WhatsApp all day.
- **Goals:** plan a movie in 2 minutes, not 2 hours. Not pay for everyone. See who paid on one screen.
- **Frustrations:** pays for all, friends forget. Friends say "take any seat", then complain.
- **Happy scenario:** searches Pushpa 2, INOX 8 PM → Create Group → code `TICK2K` → shares on WhatsApp → locks A1-A4 → assigns seats → starts Split Pay → sees Rahul PAID, Aman PENDING, Sneha PAID → taps **Remind** (opens WhatsApp with Aman's personal pay link) → all paid → booking confirmed + QR for everyone.
- **Sad scenario:** 1 minute left and Aman has not paid. Ticktit asks: "Pay for Aman (₹273.60)?" Prince taps **Cover Aman** → booking confirmed. Ticktit records "Aman owes Prince ₹273.60" (v1.1).

### Persona 2: Rahul Verma - Follower / Member
Age 20, B.Com student. Does not like installing apps.
- **Goals:** click the link, join, pay, done. Wants his own ticket.
- **Happy scenario:** WhatsApp link `ticktit.in/join/TICK2K` → sees group preview (movie, time, seats) without an account → taps Join → Continue with Google (one tap) → sees "Your seat A2 - Pay ₹273.60" → pays by UPI → green tick → own QR ticket.
- **Edge scenario:** pays, internet drops. Re-opens link → "Payment successful". The idempotency key and the gateway webhook guarantee he was charged once.

### Persona 3: Sneha Roy - Seat-conscious
Age 22, Kolkata, working professional. Wants an aisle seat and wants to see the map before paying.
- **Scenario:** joins, sees the live map with names on seats (A1 Prince, A2 Rahul, A3 Aman, A4 Sneha). Asks for A5 (aisle). Prince changes the lock to A2-A5 before payment starts. Sneha pays for A5.

### Persona 4: Sharma Family - Family user
Father 42, mother 38, kids 12 and 10, Patna. Kids have no accounts.
- **Scenario:** father creates a group alone, locks 4 seats, chooses **Pay For All** → pays once → gets 4 tickets under one booking (one group QR + 4 seat QRs). Pay For All allows more seats than members.

## 6. User Journeys

1. **Leader:** Login → Search movie → Select show → Create Group → Share link → Members join → Select seats → Lock (5 min) → Assign seats → Start Split Pay (10 min window) → Watch payment dashboard → Remind pending → All paid → Booking CONFIRMED → QR tickets to all.
2. **Member:** WhatsApp link → Group preview (no account) → Join with Google (or email + password + email OTP) → See your seat + share → Pay → Green tick + your QR ticket.
3. **Friend does not pay:** Payment window running → nudge at 3:00 left → leader prompt "Cover Aman?" at 1:00 left → if nobody covers, at 0:00 booking FAILED → seats released → every paid member auto-refunded → group stays OPEN so the leader can lock again.
4. **Two groups, same seat:** Group A locks A1 at 8:00:00. Group B tries A1 at 8:00:01 → "A1 already locked" + an instant alternative ("B3-B6 are free together - lock those?"). No double booking.
5. **Show cancelled by theatre:** admin cancels the show → every confirmed booking CANCELLED → 100% refund including convenience fee → email + in-app notice.

## 7. Features

### MVP (must have)

| ID | Feature |
|---|---|
| F1 | Auth: email + password + email OTP on every login, or Google sign-in (no OTP) (ADR-017) |
| F2 | Browse movies, theatres, shows by city and date |
| F3 | Live seat map (polling every 2 s) showing AVAILABLE / LOCKED / BOOKED and member names on assigned seats |
| F4 | Create group + join via code / WhatsApp link, public group preview |
| F5 | Seat lock 5 min (Redis) with countdown; payment window 10 min after split starts |
| F6 | Split Pay (each member pays for their own seats) + Pay For All |
| F7 | Group payment dashboard: PAID green / PENDING yellow / Remind via WhatsApp |
| F8 | Booking confirm + signed QR tickets (group QR + per-seat QR) + check-in scan |
| F9 | Automatic refunds for failed / cancelled bookings |
| F10 | My Bookings (upcoming + past) |
| F11 | Instant alternatives when a lock conflicts (nearest free block of N adjacent seats) |
| F12 | Minimal admin: add movies / theatres / shows, cancel a show, view bookings |

### v1.1 (after MVP works)
Cover a friend + IOU, member claims own seat inside the locked block, seat handover (replace a member who drops), rotating QR (anti-screenshot), ticket transfer.

### Phase 2
Plan Poll (vote on movie/time), group chat, food add-on split, user cancellation with policy refund, coupons split, mobile app, WebSocket instead of polling, Razorpay live.

### Out of scope for MVP
Bus, train, travel packages, flights (see Roadmap), full theatre admin panel, real payment gateway (mock gateway with the same async interface).

## 8. Pricing Model (summary - details in PAYMENT.md)

- Ticket price per seat comes from the show and seat category (CLASSIC / PRIME / RECLINER), GST-inclusive.
- Convenience fee ₹20 per ticket + 18% GST = **₹23.60 per ticket**.
- Example: ₹250 ticket → member pays **₹273.60** for one seat. 4 friends = ₹1,094.40.
- All amounts stored as integer paise.

## 9. Success Metrics

| Metric | Target |
|---|---|
| Group created → booking confirmed | > 40% |
| Payment started → all paid | > 70% |
| Average group size | 3.5 |
| Double booking | 0 |
| Double charge | 0 |
| Join via link (click → joined) | < 30 s |
| Groups saved by "Cover a friend" (v1.1) | tracked |

## 10. Risks

| Risk | Mitigation |
|---|---|
| Seat double-booking | Redis lock (SET NX) + conditional DB update on confirm (ADR-003) |
| Payment double-charge | Idempotency key per payment attempt + unique gateway IDs (ADR-010) |
| Friend does not pay | Nudges, Cover-a-friend prompt, auto-release + auto-refund |
| Payment lands after the deadline | Late payment auto-refunded (PAYMENT.md F5) |
| Redis down | Fail closed: locking returns 503, no unsafe fallback (ADR-015) |
| Kafka down | Transactional outbox keeps events until Kafka is back (ADR-008) |
| Ticket screenshot resale | Signed QR, one-time scan, rotating QR in v1.1 (TICKETS-QR.md) |

## 11. Competitors

| App | Group booking | Split pay | Shared live map |
|---|---|---|---|
| BookMyShow | No | No | No |
| Paytm Movies / District | No | No | No |
| **Ticktit** | Group-first | Native, per seat | Yes, with names on seats |

## 12. Roadmap - Beyond Movies

Same core everywhere: Group → Hold inventory → Each pays own share → Confirm or refund. Only the inventory changes (ADR-007).

| Phase | Vertical | What is new | Main risk |
|---|---|---|---|
| 1 (MVP) | Movies + theatre / live shows | Seat map, tiered pricing | None new - we own the seat lock |
| 2 | Bus | Partner/aggregator API owns seats; our hold must match the operator's hold time | Partner API latency and hold expiry |
| 3 | Train | Per-passenger details, waitlist / RAC, Tatkal rules | Legal: only IRCTC-authorised agents can book - licence before design |
| 4 | Stadium / events | Sections, general admission (quantity, no seat), traffic spikes when sales open | Virtual waiting room needed |
| 5 | Travel packages | One trip = several bookings (transport + stay + events) | Partial failure rules (train confirmed, hotel fails) |
| Later | Microservices | Extract services one by one (ADR proposed when Phase 2 starts) | Distributed transactions → sagas |

Rule: build each vertical only when its phase starts. Until then keep the core generic (ADR-007).
