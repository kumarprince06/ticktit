# ADR-009: Money in Paise + Per-Seat Price Model

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma

## Context
v3 used "Total = seats × price, share = total / members". That fails for ₹1000 / 3 = 333.33, ignores the convenience fee and GST, and makes the family case (4 seats, 1 member) awkward. REFUND.md also described the fee two different ways.

## Decision
- All money is stored and sent as integer **paise** (`BIGINT`). Rupee formatting only in the UI.
- Per seat payable = ticket price (from `show_prices` by category, GST-inclusive) + convenience fee 2000 + 18% GST on fee 360.
- Ticket GST is shown on the invoice: 18% for tickets above ₹100, 5% for ₹100 or less (rates in force since 22 Sep 2025). Integer maths, round half up.
- A member's share = sum of the seats assigned to them (`booking_units`). Split Pay requires every member to have at least 1 seat; Pay For All has no such rule.
- Prices are frozen into `price_snapshot` + `booking_units` at lock time.

## Alternatives Considered
- `double` - rejected: floating-point errors in money.
- `NUMERIC(10,2)` rupees - works, but paise integers are simpler and match gateway APIs.
- Equal split across members - rejected: rounding remainders and unfair when seats cost different amounts.

## Consequences
- Positive: exact sums, simple tests (4 × 27360 = 109440), fair split by seat, clear refunds.
- Negative: every API consumer must remember amounts are in paise; name fields `...Paise` to make it obvious.
