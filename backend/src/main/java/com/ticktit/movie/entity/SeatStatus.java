package com.ticktit.movie.entity;

/** Stored seat status. LOCKED is not here on purpose: locks live only in Redis (ADR-003). */
public enum SeatStatus {
    AVAILABLE,
    BOOKED,
    BLOCKED
}
