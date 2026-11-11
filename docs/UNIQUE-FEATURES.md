# Ticktit - Unique Features - v4

What makes Ticktit different from BookMyShow / Paytm / District. Each feature says **why it matters**, **how it works**, and **when** (MVP / v1.1 / Phase 2).

## Core differentiators (MVP)

### 1. Group-First Booking - MVP
- **Why:** BookMyShow books alone, then you tell friends. Ticktit creates the GROUP first, then books together.
- **How:** group = code `TICK2K` + share link + leader + members. The group is the main object, not a hack.

### 2. Split Payment, Fair by Seat - MVP
- **Why:** one person paying and chasing money is the #1 pain.
- **How:** each member pays for **the seats assigned to them** (seat price + ₹20 fee + GST). A recliner costs more than a classic seat, so whoever sits there pays more. No rounding problems, no "why do I pay the same as the recliner guy".

### 3. Live Collaborative Seat Map - MVP
- **Why:** BookMyShow's map is for one person.
- **How:** all members see the same map (polling every 2 s in MVP, WebSocket in Phase 2), with **first names on assigned seats**. When the leader locks A1, everyone sees A1 turn yellow.

### 4. Transparent Seat Lock + Payment Window - MVP
- **Why:** fairness and trust.
- **How:** "Locked for 5 min" while the leader assigns seats, then a **10-min payment window** once Split Pay starts. Visible countdown, auto-release, Redis TTL key `lock:show:{showId}:seat:{seatNo}`.

### 5. WhatsApp-First Onboarding - MVP
- **Why:** members hate installing apps.
- **How:** link → public preview (no account) → join with Google sign-in in < 30 s → pay. Viral through WhatsApp.

### 6. Pay For All + Split Pay in One Booking - MVP
- **Why:** friends split, families do not.
- **How:** the leader can switch to Pay For All any time before the window ends; it pays whatever is still unpaid.

### 7. Payment Safety Guarantee - MVP
- **Why:** money bugs kill trust.
- **How:** idempotency key per attempt, webhook-only confirmation, unique gateway IDs, conditional seat update on confirm, automatic refunds for late payments. **0 double charge, 0 double booking.**

### 8. Group Payment Dashboard - MVP
- **Why:** no "I already paid" confusion.
- **How:** PAID green, PENDING yellow, countdown, Remind button.

## New in v4 - features that make Ticktit feel different

### 9. Smart Nudges via WhatsApp (free, no SMS) - MVP
- **Why:** reminders work best where friends already talk, and SMS needs DLT registration and costs money.
- **How:** Remind opens WhatsApp with a prefilled message and the member's **personal pay link** (`wa.me/?text=...`). Automatic in-app/email nudge at 3:00 left; the leader gets "Cover Aman?" at 1:00 left.

### 10. Claim Your Seat - MVP-lite, full in v1.1
- **Why:** "take any seat" leads to complaints later (Sneha's story).
- **How:** MVP: the leader assigns seats (default join order). v1.1: after the leader locks a block, each member taps the seat they want inside the block, first come first served, before payment starts.

### 11. Instant Alternatives on Conflict - MVP
- **Why:** a 409 "seat already locked" is a dead end on other apps.
- **How:** the lock API returns the **nearest free block of N adjacent seats in the same category** (centre rows first). One tap to lock it. The same algorithm powers a "Seat together" button for N people. It's a nice DSA talking point in interviews.

### 12. Cover a Friend + IOU - v1.1
- **Why:** one late friend should not kill the whole booking.
- **How:** any member can pay a pending member's share. Ticktit records "Aman owes Prince ₹273.60" and shows Aman a one-tap UPI link to settle with Prince directly. Ticktit does not hold this money; it is only a record + reminder.

### 13. Seat Handover - v1.1
- **Why:** plans change; someone drops out after seats are locked.
- **How:** before paying, a member can leave or be removed and the leader re-shares the link; the new member inherits the seat and share without losing the lock.

### 14. Signed QR Tickets: Group QR + Seat QR - MVP
- **Why:** like BookMyShow, but built for groups: friends arrive at different times.
- **How:** one **group QR** admits the whole booking; each member gets a **seat QR** to enter alone. Tokens are signed (HMAC), one-time scan, verified at the gate (TICKETS-QR.md).

### 15. Rotating QR (anti-screenshot) + Ticket Transfer - v1.1
- **Why:** screenshot resale and "my friend can't come" are real problems.
- **How:** the in-app QR refreshes every 30 s (time-based signed token), so a screenshot stops working. Members can transfer their seat ticket to another Ticktit user before the show; the old QR is revoked.

### 16. Group Trust Score for the leader view - Phase 2
- **Why:** leaders want to know who usually pays on time.
- **How:** private, simple stat per friend ("paid on time in 5 of 6 group bookings"). Only visible inside your own groups. Opt-out available.

## Phase 2 (already planned)
Plan Poll (vote movie/time, winner auto-creates the group), group chat, food add-on split (popcorn + cold drink per member), user cancellation with policy refund, coupons applied to the group then split.

## How to pitch it in one line
"Ticktit is BookMyShow for groups: everyone sees the same seat map, pays for their own seat with a fair split, and gets their own QR. If a friend is late, you can cover them instead of losing the seats."
