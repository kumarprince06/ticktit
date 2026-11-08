# Ticktit - User Stories + Acceptance Criteria - v3 Final

US-01 As Leader, I want to create group with code, so friends can join easily. Acceptance: Click Create Group -> Code generated in <1 sec, Share link works, Code 6-char unique.
US-02 As Member, I want to join via link, so I do not type code. Acceptance: Click link -> Group page opens in <2 sec, Join button visible, No install needed.
US-03 As Member, I want to see live seat map, so I know which seats taken. Acceptance: Map shows Green/Yellow/Grey correctly, Updates when Leader selects seat.
US-04 As Leader, I want to lock seats for 5 min, so no one else takes them. Acceptance: Lock -> Timer 05:00 starts, Other group sees seats as LOCKED, After 5 min seats release.
US-05 As Member, I want to pay only my share via UPI, so I do not depend on leader. Acceptance: Pay Rs 250 -> UPI opens -> Success -> Status becomes PAID, Ticket generated.
US-06 As Member, I want QR ticket on SMS/Email, so I can enter theatre. Acceptance: After all paid, SMS + Email received within 30 sec, QR scans to Booking ID.
US-07 As Leader, I want to see who paid / not paid, so I can remind them. Acceptance: Group page shows PAID green, PENDING yellow, Remind button sends SMS.
US-08 As Leader, I want to Pay For All, so family booking is easy. Acceptance: Click Pay For All -> Pay total Rs 1000 -> All members marked PAID.
US-09 As User, I want to see My Bookings, so I can find old tickets. Acceptance: My Bookings shows upcoming + past, Click shows detail with QR.
US-10 As User, I should NOT be double-charged if I click Pay twice. Acceptance: Click Pay twice -> Charged once, Second click shows Already Paid.