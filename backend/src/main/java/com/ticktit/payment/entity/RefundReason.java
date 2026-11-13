package com.ticktit.payment.entity;

/** Why money goes back (REFUND.md). */
public enum RefundReason {
    TIMER_EXPIRED,
    LATE_PAYMENT,
    OVERPAID,
    AMOUNT_MISMATCH,
    SEAT_CONFLICT,
    SHOW_CANCELLED,
    MEMBER_REMOVED,
    ADMIN
}
