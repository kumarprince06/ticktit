# Ticktit - Product Requirements Document (PRD)
Version: 1.0 | Author: Prince Kumar Sharma | Date: Nov 2026

## 1. Problem Statement
BookMyShow, Paytm Movies are built for SOLO booking.
Real life: 80% of movie plans in India are GROUP plans - 3-5 friends, family.
Current pain:
- One person books for all, then has to ask money on WhatsApp / UPI - awkward, money stuck.
- Friends can't see same seat map together, they call each other "Which seat did you pick?"
- If one friend cancels, whole booking is messed up.
- No way to split payment at booking time.

## 2. Solution - Ticktit
Tagline: "Book Together, Pay Separately, Sit Together."
Ticktit is a group-first movie ticket booking platform.
Create group -> Invite friends via code/link -> Pick seats TOGETHER on same live map -> Each pays own share -> Booking confirmed.

## 3. Target Users
1. Primary: College students (18-24) - Siliguri, Kolkata, Patna - Group of 3-6 friends
2. Secondary: Families (30-45) - Family of 4, wants to sit together
3. Tertiary: Office colleagues - Team outing

## 4. Goals & Success Metrics
- MVP Goal: User can create group, invite 3 friends, lock seats, split pay, get ticket in < 2 min.
- Success Metrics:
    - Group creation -> Booking conversion > 40%
    - Seat lock -> Payment completion > 70%
    - Average group size: 3.5
    - Payment failure / double charge: 0%

## 5. User Personas
### Persona 1: Prince - Group Leader (21, Student)
- Always plans movie, always pays first, then chases friends for money.
- Need: "I should not pay for everyone, they should pay their own."
### Persona 2: Rahul - Group Member (20, Student)
- Gets WhatsApp link, wants to join in 1 click, see seats, pay own share via UPI.
- Need: "I don't want to install app, just link should work."

## 6. User Journey (Happy Path)
1. Prince opens Ticktit -> Searches "Pushpa 2" -> Selects INOX Siliguri, 8 PM show
2. Clicks "Create Group" -> Gets code TICK12 + shareable link
3. Shares link on WhatsApp group
4. Rahul, Aman, Sneha click link -> Join group
5. All 4 see LIVE seat map, same seats updating in real-time
6. Prince selects A1,A2,A3,A4 -> Clicks "Lock for 5 min"
7. Each member sees "Pay Rs 250" button -> Pays via UPI
8. When all 4 paid -> Screen shows "Booking Confirmed" + QR Ticket
9. Each member gets SMS + Email with ticket

## 7. Core Features (MVP - Must Have)
- F1: Auth (Register/Login via Email + OTP)
- F2: Browse Movies, Theatres, Shows
- F3: Live Seat Map
- F4: Create Group + Join via Code/Link
- F5: Seat Lock (5 min, Redis)
- F6: Split Payment (Each pays own)
- F7: Booking Confirmation + QR Ticket
- F8: My Bookings History

## 8. Non-MVP (Phase 2)
- Vote for movie/time (Poll in group)
- Chat inside group
- Food & Beverages add-on
- Offer / Coupon split
- Mobile App

## 9. Out of Scope (Will NOT Build in MVP)
- Flight / Bus / Train booking
- Theatre admin panel (use dummy data)
- Refund automation (manual for MVP)

## 10. Competitors
- BookMyShow: Solo booking, no split pay, no group map
- Paytm: Same as above
- Ticktit Edge: Group-first, Split-pay native, Live collaborative seat map

## 11. Risks
- Seat double-booking if two groups lock same seat -> Mitigation: Redis lock + DB unique constraint
- Payment double-charge if user clicks twice -> Mitigation: Idempotency Key
- Friend doesn't pay in 5 min -> Mitigation: Auto-release + Leader can pay for him / Remove him
