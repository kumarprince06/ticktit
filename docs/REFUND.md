# Ticktit - Refund Policy and Design - v3 Final

## 1. When Refund Happens (4 Cases)
Case 1 Timer Expired: 4 members, 3 paid, 1 did not pay in 5 min. Booking CANCELLED, Seats released. Refund: 3 paid members get FULL refund of share (250 each). Timeline MVP Instant (Mock) / Real 3-5 business days to original UPI. Notification SMS + Email: "Booking cancelled, refund Rs 250 initiated."
Case 2 Leader Removes PAID Member: Leader removes Aman after Aman paid 250. Aman removed, Seat released, Aman refunded FULL 250. Notification: "You were removed, refund initiated."
Case 3 User Cancels Before Show (Phase 2, Not MVP): Cancel >24h before show: 90% refund (10% convenience fee non-refundable). Cancel 2h-24h: 50% refund. Cancel <2h: 0% refund. MVP: Manual refund via support, no auto-cancel button.
Case 4 Theatre Cancels Show: Show cancelled due to rain / technical issue. All members get 100% refund including convenience fee. Notification SMS + Email to all.

## 2. What Is NOT Refundable
Convenience Fee Rs 20 per ticket - Non-refundable in Case 3 User cancel, Refundable in Case 4 Theatre cancel. If user paid and booking CONFIRMED and user just does not come -> No refund.

## 3. Technical Design
Table refunds (id, paymentId, amount, reason, status INITIATED/SUCCESS/FAILED, createdAt). Flow: Booking CANCELLED -> Kafka booking.cancelled -> Refund Consumer -> Calls Gateway Refund API -> Updates refund status. Idempotency: Refund uses Idempotency-Key = paymentId + "-refund" to prevent double refund. If refund fails: Retry 3 times via Kafka, then manual intervention.

## 4. States
INITIATED, SUCCESS, FAILED. User sees in My Bookings: "Refunded Rs 250" with date.

## 5. Examples
Example 1: Prince 250 PAID, Rahul 250 PAID, Aman 0 PENDING, Sneha 250 PAID -> Timer expires -> Prince, Rahul, Sneha each get 250 refund. Aman gets 0.
Example 2: Family Pay For All 1000, show cancelled -> Father gets 1000 full refund.