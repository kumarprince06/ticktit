# Ticktit - Scenarios - All Cases - v4

Every scenario must become an automated test (TEST-PLAN.md). Amounts assume ₹250 seats → ₹273.60 per seat with fee + GST.

## Happy Path
| ID | Scenario | Expected |
|---|---|---|
| S1 | 4 friends, all pay their own seat | Booking CONFIRMED, 4 seat QRs + group QR, emails sent |
| S2 | Family of 4, father uses Pay For All (1 member, 4 seats) | One payment ₹1,094.40, booking CONFIRMED, father holds all QRs |
| S3 | Member joins via WhatsApp link with Google sign-in, pays | Member PAID, gets own QR after confirmation |
| S4 | Leader assigns A5 to Sneha before payment | Sneha's share and map label update |

## Sad / Failure
| ID | Scenario | Expected |
|---|---|---|
| S5 | One friend does not pay in the 10-min window | Nudge at 3:00, cover prompt at 1:00, at 0:00 booking FAILED, seats released, 3 paid members refunded ₹273.60 each, group back to OPEN |
| S6 | Two groups lock the same seat | One wins; other gets 409 `SEATS_UNAVAILABLE` + alternative block |
| S7 | User pays, internet fails, retries with same key | Same payment returned, charged once |
| S8 | Leader removes a member who already paid | Member refunded, seat unassigned |
| S9 | Group full, 9th member tries | 400 `GROUP_FULL` |
| S10 | Wrong password 6 times | 429 `TOO_MANY_REQUESTS` + Retry-After; message does not reveal if the account exists |
| S11 | Payment with `failure@mock` | Payment FAILED, booking still PAYMENT_PENDING, retry with new key allowed |
| S12 | Theatre cancels the show | All bookings CANCELLED, 100% refund incl. fee, notifications |

## Edge
| ID | Scenario | Expected |
|---|---|---|
| S13 | Leader closes tab after lock | Seats auto-release at 5:00; booking RELEASED |
| S14 | Member (not leader) tries to lock seats | 403 `NOT_LEADER` |
| S15 | User tries to join a BOOKED group | 409 `GROUP_BOOKED` |
| S16 | User tries to join while payment window is open | 409 `PAYMENT_IN_PROGRESS` |
| S17 | Aman pays at 9:59, webhook arrives at 10:02 (after FAILED) | Payment SUCCESS recorded and auto-refunded (`LATE_PAYMENT`) |
| S18 | Gateway sends the same webhook twice | Second ignored; no double PAID, no double confirm |
| S19 | Redis is down when leader locks | 503 `LOCKING_UNAVAILABLE`; nothing locked |
| S20 | Kafka is down when booking confirms | Booking CONFIRMED; emails sent when Kafka returns (outbox) |
| S21 | Same QR scanned twice at the gate | Second scan 409 `ALREADY_CHECKED_IN` with first scan time |
| S22 | QR scanned for the wrong show / forged | Red "Invalid ticket"; nothing checked in |
| S23 | Split Pay with 3 seats for 4 members | 400 `SEATS_LESS_THAN_MEMBERS` |
| S24 | Refund call to gateway fails 3 times | Refund FAILED, admin alerted, retry button in admin |

## v1.1
| ID | Scenario | Expected |
|---|---|---|
| S25 | Leader covers Aman at 0:45 left | Aman PAID (beneficiary), Prince payer, IOU created, booking CONFIRMED |
| S26 | Member drops before paying, leader hands seat to new friend | Seat stays locked, new member assigned, share moves |
| S27 | Screenshot of a rotating QR used 2 minutes later | Rejected: token expired |
