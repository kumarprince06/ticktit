# Ticktit - Product Requirements Document (PRD) - v3 Final
Version: 3.0 | Author: Prince Kumar Sharma | Date: 08 Nov 2026

## 1. Executive Summary
Ticktit is a group-first booking platform for India. It starts with movie tickets and grows into theatre / live shows, bus, train and full travel packages - all with the same group + split-pay experience.
Tagline: "Book Together, Pay Separately, Sit Together."
BookMyShow solves: "I want to book a ticket."
Ticktit solves: "We want to book TOGETHER, sit TOGETHER, and pay SEPARATELY without awkward money chasing."

## 2. Problem Statement
In India, 80% movie plans are group plans. But all apps are built for solo booking.
Real WhatsApp example:
Prince: "Pushpa 2, Saturday 8 PM, INOX Siliguri, shall we go?"
Rahul: "Yes, 2 tickets"
Aman: "1 for me too"
Prince books 4 tickets, pays Rs 1000. Next day Prince: "Bro, send me Rs 250." Aman forgets. Prince loses money + friendship awkwardness.

Pain Points:
1. Money Pain: Leader pays for all, then chases money.
2. Seat Pain: Friends cannot see same seat map. They call: "Which seats did you take?"
3. Coordination Pain: If one friend cancels, whole booking messed up.
4. Trust Pain: Friends do not know if seat is actually booked.

## 3. Solution
Create Group -> Share Code/Link -> Friends Join -> See SAME Live Seat Map -> Pick Seats Together -> Each Pays Own Share -> All Get QR Ticket.

## 4. Target Users
Primary: College students 18-24, Siliguri, Kolkata, Patna, Group 3-6 friends.
Secondary: Families 30-45, Family of 4, wants to sit together.
Tertiary: Office colleagues, Team outing 5-8 people.

## 5. User Personas - Full

### Persona 1: Prince Kumar Sharma - Planner / Group Leader
Age 21, Siliguri, B.Tech Student + Developer, Android, UPI PhonePe, WhatsApp 24x7.
Goals: Plan movie in 2 min not 2 hours. Should NOT pay for everyone. See who paid, who did not in one screen.
Frustrations: Pays Rs 1000, friends forget. Friends say "Take any seat" then complain "We wanted a corner". Has to send screenshot of seats.
Happy Scenario: Opens Ticktit, searches Pushpa 2, INOX 8 PM, Creates Group -> Code TICK12, Shares on WhatsApp, Selects A1-A4, Locks, Sees Rahul PAID, Aman PENDING, Sneha PAID, Clicks Remind Aman, All paid -> Booking Confirmed + QR.
Sad Scenario: Aman does not pay in 5 min. Timer expires. Prince sees: "Aman removed, seats released. Lock again with 3 members or pay for Aman." Prince chooses Pay For Aman -> Pays Rs 250 extra -> Booking confirmed.

### Persona 2: Rahul Verma - Follower / Member
Age 20, Siliguri, B.Com Student, Android, UPI, does not like installing apps.
Goals: Click the link, join, pay, done. Wants to see own seat.
Frustrations: Has to install app just to see seat. Leader says "Booked it" - Rahul wants own ticket.
Happy Scenario: Gets WhatsApp link ticktit.in/join/TICK12, Clicks, Sees Prince Group - Pushpa 2 - INOX 8 PM - Seats A1-A4, Clicks Join, Enters name + phone, Sees Pay Rs 250, Pays via UPI, Sees PAID green tick, Gets SMS with QR.
Edge Scenario: Pays, internet disconnects. Thinks money deducted, ticket not shown. Re-opens link, Ticktit shows Payment Successful because Idempotency-Key prevented double charge.

### Persona 3: Sneha Roy - Picky / Seat Conscious
Age 22, Kolkata, Working Professional. Wants corner / aisle seat, wants to see seat map before paying.
Goals: Know who sits where. If she does not like the seat, she will not join.
Scenario: Joins group, sees live map A1-Prince, A2-Rahul, A3-Aman, A4-Sneha. Says "I want A5, not A4." Prince changes to A5 before lock. Sneha happy, pays.

### Persona 4: Sharma Family - Family User
Father 42, Mother 38, Kids 12, 10, Patna. Father books for family, wants all 4 together, wants to pay in one go.
Scenario: Father creates group, adds family, Selects Pay For All -> Pays Rs 1000 once -> Gets 4 tickets in one QR. This is why Pay For All is needed.

## 6. User Journeys
Journey 1 - Leader: Login -> Search Movie -> Select Theatre/Show -> Create Group -> Share Link -> Wait for join -> Select Seats -> Lock 5 min -> See Payment Status -> Remind Pending -> All Paid -> Booking Confirmed + QR + SMS/Email to all.
Journey 2 - Member: Click WhatsApp Link -> See Group Info -> Join -> See Seat Map + Your Seat -> Pay Rs 250 -> UPI Success -> Green Tick + QR Ticket.
Journey 3 - Failure Friend Does Not Pay: Lock, Timer 5 min, Aman does not pay, Timer 00:00, Seats auto-release, Leader sees Booking Cancelled - Aman did not pay. Paid members refunded. Leader can create new group with 3 members OR Pay for Aman.
Journey 4 - Double Booking: Group A locks A1 at 8:00:00, Group B tries A1 at 8:00:01, System: "A1 already locked", Group B must choose A5. No double booking.

## 7. Features
MVP Must Have: F1 Auth, F2 Browse Movies/Theatres/Shows, F3 Live Seat Map, F4 Create Group + Join via Code/Link, F5 Seat Lock 5 min Redis, F6 Split Payment + Pay For All, F7 Booking Confirm + QR Ticket, F8 My Bookings.
Phase 2: Poll Vote for Movie/Time, Group Chat, Food Add-on, Coupons Split, Mobile App, Google Login.
Out of Scope for MVP: Bus, Train, Travel Packages (see Roadmap), Flight, Theatre Admin Panel, Refund Automation (manual in MVP).

## 8. Success Metrics
Group Creation to Booking > 40%, Lock to Pay Completion > 70%, Avg Group Size 3.5, Double Booking 0, Double Charge 0, Join via Link < 30 sec.

## 9. Risks
Seat double-booking -> Redis Lock + DB Unique Constraint. Payment double-charge -> Idempotency-Key. Friend does not pay -> Auto-release + Leader Pay-For-All. Redis down -> Fallback to DB lock.

## 10. Competitors
BookMyShow: Solo, No Split, No Group Map. Paytm Movies: Solo, Discounts, No Group. Ticktit Edge: Group-first, Split-pay native, Live collaborative map, WhatsApp-first onboarding.

## 11. Roadmap - Beyond Movies
Same core everywhere: Group -> Hold inventory -> Each pays own share -> Confirm or refund. Only the inventory changes. See ADR-007.

| Phase | Vertical | What is new | Main risk |
| --- | --- | --- | --- |
| 1 (MVP) | Movies + Theatre / Live Shows | Seat map, tiered pricing for live shows | None new - we own the seat lock |
| 2 | Bus | Partner / aggregator API owns the seats; our hold must match the operator's hold time | Partner API latency and hold expiry |
| 3 | Train | Per-passenger details (name, age, ID), waitlist / RAC, Tatkal rules | Legal: only IRCTC-authorised agents can book. Licence must be sorted before design |
| 4 | Travel Packages | One trip = several bookings (transport + stay + events) | Partial failure: train confirmed but hotel fails - needs cancel / replace rules |

Rule: build each vertical only when its phase starts. Until then, only keep the core generic (ADR-007).
