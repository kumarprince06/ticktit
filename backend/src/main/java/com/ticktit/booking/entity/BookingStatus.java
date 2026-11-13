package com.ticktit.booking.entity;

import java.util.Set;

/**
 * architecture.md §3. LOCKED, PAYMENT_PENDING and CONFIRMED are named in V4's unique indexes: never rename them.
 * Each status lists where it may go next; Booking.transitionTo refuses anything else.
 */
public enum BookingStatus {
    LOCKED,
    PAYMENT_PENDING,
    CONFIRMED,
    RELEASED,
    FAILED,
    CANCELLED;

    public boolean canMoveTo(BookingStatus next) {
        return switch (this) {
            case LOCKED -> Set.of(RELEASED, PAYMENT_PENDING).contains(next);
            case PAYMENT_PENDING -> Set.of(CONFIRMED, FAILED).contains(next);
            case CONFIRMED -> next == CANCELLED;
            case RELEASED, FAILED, CANCELLED -> false;
        };
    }

    public boolean isActive() {
        return this == LOCKED || this == PAYMENT_PENDING;
    }
}
