# Ticktit - Payment Design - v4

## 1. Price Model (ADR-009)

All amounts are integer **paise**.

| Part | Rule | Example (PRIME seat) |
|---|---|---|
| Ticket price | From `show_prices` for the seat's category. GST-inclusive, as cinemas show it | ₹250.00 = 25000 |
| Ticket GST (invoice only) | Already inside the price: 18% if ticket > ₹100, 5% if ≤ ₹100 (GST rates in force since 22 Sep 2025) | 25000 − round(25000 / 1.18) = 3814 |
| Convenience fee | ₹20 per ticket | 2000 |
| GST on fee | 18% of the fee | 360 |
| **Per seat payable** | ticket + fee + fee GST | **27360 = ₹273.60** |

- **Booking total** = sum of all seats' payable amounts. 4 PRIME seats = 109440 (₹1,094.40).
- **Member share (Split Pay)** = sum of the seats assigned to that member. Fair by seat, no rounding problems.
- **Price snapshot:** the full breakdown is frozen into `bookings.price_snapshot` and `booking_units` at lock time. Later price changes never affect an active booking.
- The server always computes amounts. Any amount sent by the client is ignored.

## 2. Payment Models

| Model | Who pays | When allowed | Result |
|---|---|---|---|
| **A. Split Pay** (friends) | Each member pays their own share | Booking LOCKED → leader starts split; every member must have ≥ 1 seat | CONFIRMED when paid = total |
| **B. Pay For All** (family) | Leader pays everything still unpaid | LOCKED (starts the window) or PAYMENT_PENDING (pays the remaining amount) | All members marked PAID |
| **C. Cover a Friend** (v1.1) | Any member pays one pending member's share | PAYMENT_PENDING | Beneficiary marked PAID, IOU recorded |

## 3. Payment Flow (async, same for Mock and Razorpay - ADR-011)

1. Leader locks seats → booking **LOCKED** (5 min).
2. Leader `POST /payments/split` → booking **PAYMENT_PENDING**, `payment_deadline = now + 10 min`, Redis lock extended, shares shown on the group page.
3. Member taps **Pay ₹273.60** → frontend creates a new UUID for this attempt (kept in memory/sessionStorage until the attempt ends) and sends `POST /payments/pay` with `Idempotency-Key`.
4. Backend: if the key exists for this user → return the stored response (no new order). Else compute the share, create `payments` row **PENDING**, call `gateway.createOrder(amount, ref)` → return `gatewayOrderId` + checkout data.
5. Frontend opens checkout (MVP: mock page asks for VPA `success@mock` / `failure@mock`; Phase 2: Razorpay Checkout).
6. Gateway calls `POST /webhooks/payment` with an HMAC signature. Backend verifies the signature, stores the event in `webhook_events` (duplicate event ID → ignored), sets payment **SUCCESS** or **FAILED**, member PAID if their share is fully covered, writes outbox `payment.succeeded`.
7. In the same transaction: if paid = total → **confirm** (seats BOOKED via conditional update, booking CONFIRMED, group BOOKED, tickets, outbox `booking.confirmed`).
8. Not all paid → stays PAYMENT_PENDING. The UI polls `GET /groups/{id}`.
9. Deadline passes with unpaid shares → scheduler sets booking **FAILED**, releases seats, creates refunds for every SUCCESS payment (REFUND.md).

Nudges: automatic reminder at 3:00 left; leader prompt "Cover Aman?" at 1:00 left (v1.1 cover, MVP shows Pay For All).

## 4. Idempotency (ADR-010)

- **Problem:** double-click, network retry, user re-opens the page.
- **Rule:** one key per **payment attempt**. Network retries and double-clicks reuse the key → same result. After a FAILED attempt, a new Pay tap creates a **new** key (otherwise the user could never retry).
- **Storage:** `idempotency_keys (user_id, idem_key, request_hash, response)` for 24 h + `payments UNIQUE (payer_user_id, idempotency_key)`.
- Same key, different body → `422 IDEMPOTENCY_KEY_REUSED`.
- Gateway side: `gateway_order_id` and `gateway_txn_id` are UNIQUE, so one gateway payment can never be counted twice.

## 5. Failure Cases

| ID | Case | Handling |
|---|---|---|
| F1 | Gateway timeout when creating the order | Payment stays PENDING; retry with the **same** key returns the same row; reconciliation checks the gateway |
| F2 | User closes the UPI app / VPA `failure@mock` | Webhook FAILED; user can retry with a new key while the window is open |
| F3 | Webhook never arrives | Reconciliation job (every 5 min) asks the gateway for PENDING payments older than 2 min |
| F4 | Double-click Pay | Same key → one payment |
| F5 | Payment SUCCESS after booking FAILED/CONFIRMED | Kept as SUCCESS and auto-refunded with reason `LATE_PAYMENT` |
| F6 | Webhook with bad signature | 400, nothing changes, security log entry |
| F7 | Amount in webhook ≠ payment amount | Payment marked FAILED for review, admin alert, refund if captured |
| F8 | Confirm finds a seat no longer AVAILABLE (should never happen) | Booking FAILED, everyone refunded, ERROR alert |

## 5.1 Mock Gateway (MVP)

- Implements the same `PaymentGateway` interface as Razorpay: `createOrder`, `fetchPayment`, `refund`, `verifyWebhook`.
- `POST /mock-gateway/orders/{id}/complete {vpa}` (dev profile only) → after ~1 s it sends a signed webhook to `/webhooks/payment`.
- VPA `success@mock` → SUCCESS, `failure@mock` → FAILED, `timeout@mock` → no webhook (tests reconciliation).
- Why mock: demo without Razorpay KYC/bank account. Switching to Razorpay test mode later = new adapter only (`success@razorpay` / `failure@razorpay` work the same way there).

## 6. Security

- Never store card number, CVV or UPI PIN. Only gateway IDs, amount, method, status.
- Amount always computed server-side from `booking_units`.
- All payment APIs require a JWT; the payer comes from the token.
- Webhook endpoint is public but **HMAC-verified on the raw body** before parsing, and processed only once per event ID.
- Every payment and refund is append-only history (status changes only), so money is traceable.
