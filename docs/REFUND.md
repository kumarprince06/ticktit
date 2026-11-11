# Ticktit - Refund Policy and Design - v4

Refunds are **automatic** in the MVP. Each member gets their own refund for their own payment.

## 1. When Refunds Happen

| Case | Trigger | Refund amount | Seats | When |
|---|---|---|---|---|
| 1. Payment window expired | Not all shares paid by the deadline → booking FAILED | 100% of each SUCCESS payment (incl. fee) | Released | MVP |
| 2. Leader removes a PAID member | `DELETE /groups/{id}/members/{userId}` before CONFIRMED | 100% of that member's payment | Unassigned (stay locked for the group) | MVP |
| 3. Late payment | Payment SUCCESS arrives after the booking is FAILED/CONFIRMED by others | 100% of that payment | - | MVP |
| 4. Theatre cancels the show | Admin cancels → bookings CANCELLED | 100% incl. convenience fee | Released | MVP |
| 5. User cancels before the show | User-initiated | Ticket price only, by tier below; fee not refundable | Released for resale | Phase 2 |

Case 5 tiers (Phase 2): more than 24 h before the show → 100% of ticket price; 2-24 h → 50%; less than 2 h → no cancellation. Convenience fee (₹20 + GST per ticket) is never refunded when the **user** cancels, and always refunded when Ticktit or the theatre cancels (Cases 1-4).

If the booking is CONFIRMED and the user simply does not come → no refund.

## 2. Technical Design

**Table:** `refunds (payment_id UNIQUE, booking_id, amount_paise, reason, status, attempts, gateway_refund_id)` - one refund per payment in MVP.

**Flow**
1. The business event (deadline FAILED, member removed, late payment, show cancelled) and the `refunds` rows (**INITIATED**) and the outbox event `refund.requested` are written in **one transaction**.
2. Outbox relay → Kafka `refund.requested` → refund consumer.
3. Consumer (idempotent via `processed_events`) calls `gateway.refund(paymentId, amount, reference = "refund-{refundId}")`. The reference is the gateway idempotency key, so a retry never refunds twice.
4. Gateway response or `refund.processed` / `refund.failed` webhook → status **SUCCESS** or **FAILED**.
5. Failure → retry 3 times (1 min, 5 min, 15 min) → still failing → **FAILED**, admin alert, "Retry refund" button in admin.
6. Reconciliation job every 5 min re-checks refunds INITIATED for more than 10 min.
7. Outbox `refund.completed` → notification email + in-app: "Refund ₹273.60 initiated / completed".

**Timelines shown to users:** mock gateway = instant. Real gateway = usually a few business days back to the original UPI/card, depending on the bank.

## 3. States

`INITIATED → SUCCESS` or `INITIATED → FAILED (→ INITIATED on admin retry)`.
My Bookings shows "Refunded ₹273.60 on 21 Nov" or "Refund in progress".

## 4. Examples (₹250 seats → ₹273.60 per seat)

**Example 1 - Aman does not pay:** Prince PAID 27360, Rahul PAID 27360, Sneha PAID 27360, Aman PENDING 0 → deadline passes → booking FAILED → Prince, Rahul and Sneha each get 27360 back. Aman pays nothing. The group stays OPEN; Prince can lock again for 3 or cover Aman next time.

**Example 2 - Family, show cancelled:** Father paid 109440 with Pay For All → theatre cancels → father gets 109440 (incl. fees) back.

**Example 3 - Late payment:** Aman pays at 9:59; the webhook arrives at 10:02 after the booking FAILED → Aman's 27360 is refunded automatically with reason `LATE_PAYMENT`.
