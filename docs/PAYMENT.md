# Ticktit - Payment Design - v3 Final

## 1. Payment Models
Model A Split Pay (Main): Total = Seats * Price. Example 4 * 250 = 1000. Share = 250 each. Each member pays own share. Booking CONFIRMED only when ALL paid. Use Case: Friends.
Model B Pay For All (Family): Leader pays total 1000 in one go. All members marked PAID. Use Case: Family.

## 2. Payment Flow
1. Leader locks seats -> Booking LOCKED, Timer 5 min.
2. System creates PaymentIntent per member: bookingId, userId, amount, status PENDING, idempotencyKey.
3. Member clicks Pay Rs 250 -> Frontend sends Idempotency-Key UUID (generated once, stored in localStorage).
4. Backend checks: If idempotencyKey exists -> Return previous result, DO NOT charge again.
5. Call Mock Gateway (MVP) / Razorpay (Phase 2) -> SUCCESS / FAILURE.
6. If SUCCESS: Payment SUCCESS, Member PAID, Check if ALL PAID.
7. If ALL PAID: Booking CONFIRMED, Seats BOOKED, Kafka booking.confirmed -> SMS + Email.
8. If NOT ALL PAID: Booking PENDING, Wait for others.
9. If Timer expires and NOT ALL PAID: Booking CANCELLED -> Trigger Refund (see REFUND.md).

## 3. Idempotency
Problem: User clicks Pay twice, or internet disconnects and retries. Solution: Client generates UUID once. Backend stores it UNIQUE. Table payments (idempotency_key UNIQUE). If same key comes again -> Return same payment, do NOT call gateway again.

## 4. Gateway MVP vs Real
MVP: Mock Gateway - Always SUCCESS except amount 999 -> FAILURE for testing. Phase 2: Razorpay / PhonePe - Real UPI, Cards. Why Mock in MVP: Demo without Razorpay KYC, bank account.

## 5. Failure Cases
F1 Gateway timeout -> Payment PENDING, Retry with SAME Idempotency-Key. F2 User closes UPI app -> FAILED, Can retry. F3 Amount mismatch -> Error: Amount mismatch, expected 250, got 200. F4 Double-click -> Charged once.

## 6. Security
Never store Card number / UPI PIN in DB. Only store gateway transactionId, amount, status. Amount verified server-side: Frontend amount ignored, backend calculates Share from DB price. All payment APIs require JWT.