package com.ticktit.common.inventory;

import com.ticktit.common.enums.ItemType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only way core modules (group, booking, payment) learn about and reserve the thing being booked (ADR-007).
 * Each vertical implements it: the movie module for SHOW. Core never imports a vertical.
 * A "unit" is one bookable piece: a seat number for movies, a berth for trains later.
 * A "holder" is the group's public id: the owner of a hold.
 */
public interface InventoryProvider {

    ItemType type();

    /** A short, display-ready description, or empty if the item doesn't exist. */
    Optional<ItemSummary> describe(long itemId);

    /**
     * All-or-nothing: either every unit is held for the holder for {@code ttl}, or none is and the result lists
     * the units someone else has (or that are already sold). Holding units you already hold just extends them.
     * Fails closed: if the lock store is down this throws (503), it never lets the hold through (ADR-015).
     */
    HoldResult hold(long itemId, List<String> units, String holder, Duration ttl);

    /** Gives the holder's units back. Units held by someone else are left alone. */
    void release(long itemId, List<String> units, String holder);

    /**
     * The final double-booking guard (architecture.md §4.3): marks the units sold to this booking only if every one
     * is still available, in one conditional update. Returns how many were sold; fewer than asked = conflict, and the
     * caller must roll back.
     */
    int confirm(long itemId, List<String> units, long bookingId);

    /** Current price and category of each unit (GST-inclusive, paise). Unknown units are left out. */
    Map<String, UnitPrice> price(long itemId, List<String> units);

    /** The nearest block of free units like the ones wanted (same size and category), if there is one. */
    Optional<List<String>> suggestAlternative(long itemId, List<String> wanted);

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

    record HoldResult(boolean held, List<String> unavailable) {
        public static HoldResult ok() {
            return new HoldResult(true, List.of());
        }
    }

    record UnitPrice(String category, long pricePaise) {
    }
}
