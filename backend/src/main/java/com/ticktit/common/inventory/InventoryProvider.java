package com.ticktit.common.inventory;

import com.ticktit.common.enums.ItemType;

import java.time.Instant;
import java.util.Optional;

/**
 * The only way core modules (group, booking, payment) learn about the thing being booked (ADR-007).
 * Each vertical implements it: the movie module for SHOW. Core never imports a vertical.
 * Seat hold/release/confirm/price are added here by the booking module.
 */
public interface InventoryProvider {

    ItemType type();

    /** A short, display-ready description, or empty if the item doesn't exist. */
    Optional<ItemSummary> describe(long itemId);

    /**
     * @param title    e.g. the movie title
     * @param venue    e.g. "Aurora Cinemas, City Centre · Audi 2"
     * @param detail   e.g. "Hindi · IMAX 2D"
     * @param link     the web path that shows the item, e.g. "/movies/paper-kites"
     * @param bookable false once it has started, been cancelled, or booking isn't open yet
     */
    record ItemSummary(long itemId, String title, String venue, String city, String detail, Instant startsAt,
                       String link, boolean bookable) {
    }
}
