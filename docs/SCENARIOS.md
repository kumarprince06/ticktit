# Ticktit - Scenarios - All Cases - v3 Final

## Happy Path
- S1 4 friends, all pay -> Booking confirmed in 2 min.
- S2 Family of 4, father pays for all -> Booking confirmed.
- S3 Member joins via link, pays -> Gets ticket.

## Sad / Failure
- S4 One friend does not pay in 5 min -> Lock expires, booking cancelled, paid members refunded.
- S5 Two groups try the same seat -> One wins, the other gets 409.
- S6 User pays, internet fails -> Retry with the same Idempotency-Key, no double charge.
- S7 Leader removes member after payment -> Refund + seat released.
- S8 Group full -> 9th member blocked (400).
- S9 Wrong password 5 times -> Account locked 15 min (423).
- S10 Payment gateway fails (test amount 999) -> Booking PENDING, retry allowed.

## Edge
- S11 Leader closes tab after lock -> Seats auto-release after 5 min.
- S12 Member (not leader) tries to select seats -> 403 "Only leader can select seats."
- S13 User tries to join a booked group -> 409 "Group already booked."
