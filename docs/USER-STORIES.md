# Ticktit - User Stories + Acceptance Criteria - v4

Format: As a <role>, I want <goal>, so that <reason>. Acceptance criteria are testable. Requirement links point to SRS.md.

## MVP

**US-01 Create group** - As a leader, I want to create a group with a code, so friends can join easily. (FR-03.1)
- Code generated in < 1 s, 6 chars, no O/0/I/1, unique.
- Share link `ticktit.in/join/{code}` opens WhatsApp share sheet with a prefilled message.

**US-02 Join via link** - As a member, I want to join from a WhatsApp link, so I do not type a code or install an app. (FR-03.2, FR-03.3, FR-01.4)
- Link opens the group preview in < 2 s without login.
- Join with Google sign-in in < 30 s end to end (email + password + email OTP also works).
- Full / booked / cancelled / payment-in-progress groups show a clear message.

**US-03 Live seat map** - As a member, I want to see the same seat map as my leader, so I know which seats we have. (FR-04.1)
- Green / yellow / grey legend; my group's seats outlined with first names.
- Updates within 2 s of the leader locking or assigning.

**US-04 Lock seats** - As a leader, I want to lock seats for 5 minutes, so no one else takes them. (FR-04.4 - FR-04.6)
- Timer 05:00 starts; other groups see the seats as LOCKED.
- If any seat is taken, nothing is locked and an alternative block is suggested.
- After 5 min without starting payment, seats are released.

**US-05 Assign seats** - As a leader, I want to assign seats to members, so everyone knows where they sit and what they pay. (FR-04.7)
- Default assignment in join order; drag/tap to change before payment starts.
- Share per member updates instantly (seat price + fee).

**US-06 Pay my own share** - As a member, I want to pay only for my seat via UPI, so I do not depend on the leader. (FR-05.3, FR-05.4)
- Pay ₹273.60 → status PAID only after the gateway confirms.
- Closing the app mid-payment and reopening shows the true status.

**US-07 No double charge** - As a user, I must not be charged twice if I click Pay twice or my network retries. (FR-05.3, EC-02)
- Two clicks within 1 s → one payment; second click shows "Already paid / processing".

**US-08 Payment dashboard + remind** - As a leader, I want to see who paid and remind the rest, so I do not chase money manually. (FR-03.4, FR-06.4)
- PAID green, PENDING yellow, countdown visible.
- Remind opens WhatsApp with the member's personal pay link; max 1 per minute per member.

**US-09 Pay For All** - As a parent, I want to pay for the whole family in one go, so booking is simple. (FR-05.7)
- Works with 1 member and up to 8 seats.
- All seats confirmed with one payment; all QRs shown to the leader.

**US-10 Automatic refund** - As a member who paid, I want my money back automatically if the group fails, so I never have to ask. (FR-05.8, REFUND.md)
- Booking FAILED → refund INITIATED within 1 min; status visible in My Bookings.

**US-11 QR ticket** - As a member, I want my own QR ticket, so I can enter even if my friends are late. (FR-07.1, FR-07.2)
- Email within 30 s of confirmation; QR visible in My Bookings.
- Group QR admits the whole booking; seat QR admits one seat.

**US-12 Check-in** - As theatre staff, I want to scan QR tickets, so only valid tickets enter once. (FR-07.3)
- Valid → green with seat numbers; reused / wrong show / forged → red with reason.

**US-13 My Bookings** - As a user, I want to see upcoming and past bookings with QR and refund status. (FR-06.1)

**US-14 Seat conflict alternative** - As a leader, if my seats get taken, I want the nearest free block suggested, so I can lock again in one tap. (FR-04.5)

**US-15 Show cancelled** - As a member, if the theatre cancels the show, I want a full refund including fees automatically. (FR-08.2)

## v1.1

**US-16 Cover a friend** - As a leader, when a friend has not paid near the deadline, I want to pay their share, so the booking is not lost. (FR-05.11)
- Prompt at 1:00 left; covered member marked PAID; IOU "Aman owes Prince ₹273.60" shown to both with a UPI settle link.

**US-17 Claim my seat** - As a member, I want to pick my own seat inside the locked block, so I sit where I like. (UNIQUE-FEATURES #10)

**US-18 Seat handover** - As a leader, if a member drops before paying, I want to give their seat to a new friend without losing it. (UNIQUE-FEATURES #12)

**US-19 Rotating QR + transfer** - As a member, I want my QR to refresh so screenshots cannot be resold, and to transfer my ticket to a friend before the show. (TICKETS-QR.md)
