package com.ticktit.movie.service;

import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.enums.ItemType;
import com.ticktit.common.exception.ApiException;
import com.ticktit.common.inventory.InventoryProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The movie vertical's side of ADR-007: describes, holds and prices show seats for the core.
 * Seat holds live in Redis only (ADR-003): key lock:show:{showId}:seat:{seatNo} = holder (group public id), TTL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShowInventory implements InventoryProvider {

    /**
     * All-or-nothing hold in one atomic step (Redis runs a script without interruption, so two groups can never
     * both pass the check). Returns the 1-based positions of keys held by someone else; empty = all held now.
     */
    private static final RedisScript<List> HOLD = RedisScript.of("""
            local taken = {}
            for i, key in ipairs(KEYS) do
              local owner = redis.call('GET', key)
              if owner and owner ~= ARGV[1] then table.insert(taken, i) end
            end
            if #taken > 0 then return taken end
            for _, key in ipairs(KEYS) do redis.call('SET', key, ARGV[1], 'PX', ARGV[2]) end
            return taken
            """, List.class);

    /** Deletes only the keys this holder owns, so a late release can't free someone else's seat. */
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            local n = 0
            for _, key in ipairs(KEYS) do
              if redis.call('GET', key) == ARGV[1] then n = n + redis.call('DEL', key) end
            end
            return n
            """, Long.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    @Override
    public ItemType type() {
        return ItemType.SHOW;
    }

    @Override
    public Optional<ItemSummary> describe(long showId) {
        return jdbc.query("""
                SELECT s.id, m.slug, m.title, t.name AS theatre, t.area, sc.name AS screen, c.name AS city,
                       s.language, s.format, s.starts_at,
                       (s.status = 'SCHEDULED' AND s.booking_opens_at <= now() AND s.starts_at > now()) AS bookable
                FROM shows s
                JOIN movies m ON m.id = s.movie_id
                JOIN screens sc ON sc.id = s.screen_id
                JOIN theatres t ON t.id = sc.theatre_id
                JOIN cities c ON c.id = t.city_id
                WHERE s.id = :id""", Map.of("id", showId), (rs, i) -> new ItemSummary(
                rs.getLong("id"), rs.getString("title"),
                rs.getString("theatre") + ", " + rs.getString("area") + " · " + rs.getString("screen"),
                rs.getString("city"), rs.getString("language") + " · " + rs.getString("format"),
                rs.getTimestamp("starts_at").toInstant(), "/movies/" + rs.getString("slug"),
                rs.getBoolean("bookable"))).stream().findFirst();
    }

    @Override
    public HoldResult hold(long showId, List<String> seats, String holder, Duration ttl) {
        // 1. Postgres: every seat must exist and not be sold or blocked
        Map<String, String> stored = new HashMap<>();
        jdbc.query("SELECT seat_no, status FROM show_seats WHERE show_id = :show AND seat_no IN (:seats)",
                Map.of("show", showId, "seats", seats), rs -> {
                    stored.put(rs.getString("seat_no"), rs.getString("status"));
                });
        List<String> unavailable = seats.stream().filter(s -> !"AVAILABLE".equals(stored.get(s))).toList();
        if (!unavailable.isEmpty()) {
            return new HoldResult(false, unavailable);
        }
        // 2. Redis: take every seat or none
        List<?> taken;
        try {
            taken = redis.execute(HOLD, keys(showId, seats), holder, String.valueOf(ttl.toMillis()));
        } catch (RuntimeException e) {
            log.error("Seat lock store unavailable", e);
            throw new ApiException(ErrorCode.LOCKING_UNAVAILABLE);   // fail closed (ADR-015)
        }
        if (taken == null || taken.isEmpty()) {
            return HoldResult.ok();
        }
        return new HoldResult(false, taken.stream().map(i -> seats.get(((Number) i).intValue() - 1)).toList());
    }

    @Override
    public void release(long showId, List<String> seats, String holder) {
        if (seats.isEmpty()) {
            return;
        }
        try {
            redis.execute(RELEASE, keys(showId, seats), holder);
        } catch (RuntimeException e) {
            log.warn("Couldn't release seat locks for show {}; they expire on their own: {}", showId, e.getMessage());
        }
    }

    @Override
    public int confirm(long showId, List<String> seats, long bookingId) {
        return jdbc.update("""
                UPDATE show_seats SET status = 'BOOKED', booking_id = :booking, version = version + 1
                WHERE show_id = :show AND seat_no IN (:seats) AND status = 'AVAILABLE'""",
                Map.of("booking", bookingId, "show", showId, "seats", seats));
    }

    @Override
    public Map<String, UnitPrice> price(long showId, List<String> seats) {
        Map<String, UnitPrice> prices = new LinkedHashMap<>();
        jdbc.query("""
                SELECT ss.seat_no, ss.category, p.price_paise
                FROM show_seats ss JOIN show_prices p ON p.show_id = ss.show_id AND p.category = ss.category
                WHERE ss.show_id = :show AND ss.seat_no IN (:seats)""",
                Map.of("show", showId, "seats", seats), rs -> {
                    prices.put(rs.getString("seat_no"), new UnitPrice(rs.getString("category"), rs.getLong("price_paise")));
                });
        return prices;
    }

    /**
     * Nearest run of {@code wanted.size()} side-by-side free seats in the category of the first wanted seat.
     * Side by side = next to each other on the drawn map (an aisle breaks a run). Nearest = fewest rows away,
     * then closest to the middle of what was wanted.
     */
    @Override
    public Optional<List<String>> suggestAlternative(long showId, List<String> wanted) {
        record Seat(String no, int gridRow, int gridCol, String category, boolean free) {
        }
        Map<String, String> locks = lockedSeats(showId);
        List<Seat> all = jdbc.query("""
                SELECT ss.seat_no, sl.grid_row, sl.grid_col, ss.category, ss.status
                FROM show_seats ss JOIN screen_seats sl ON sl.id = ss.screen_seat_id
                WHERE ss.show_id = :show ORDER BY sl.grid_row, sl.grid_col""", Map.of("show", showId),
                (rs, i) -> new Seat(rs.getString("seat_no"), rs.getInt("grid_row"), rs.getInt("grid_col"),
                        rs.getString("category"),
                        "AVAILABLE".equals(rs.getString("status")) && !locks.containsKey(rs.getString("seat_no"))));
        List<Seat> target = all.stream().filter(s -> wanted.contains(s.no())).toList();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        String category = target.getFirst().category();
        double wantRow = target.stream().mapToInt(Seat::gridRow).average().orElse(0);
        double wantCol = target.stream().mapToInt(Seat::gridCol).average().orElse(0);
        int n = wanted.size();

        List<List<Seat>> runs = new ArrayList<>();
        Map<Integer, List<Seat>> byRow = new LinkedHashMap<>();
        all.stream().filter(s -> s.category().equals(category)).forEach(s -> byRow.computeIfAbsent(s.gridRow(), r -> new ArrayList<>()).add(s));
        for (List<Seat> row : byRow.values()) {
            for (int start = 0; start + n <= row.size(); start++) {
                List<Seat> window = row.subList(start, start + n);
                boolean together = true;
                for (int k = 1; k < n && together; k++) {
                    together = window.get(k).gridCol() == window.get(k - 1).gridCol() + 1;
                }
                if (together && window.stream().allMatch(Seat::free)) {
                    runs.add(window);
                }
            }
        }
        return runs.stream()
                .min(Comparator.comparingDouble((List<Seat> w) -> Math.abs(w.getFirst().gridRow() - wantRow))
                        .thenComparingDouble(w -> Math.abs(w.stream().mapToInt(Seat::gridCol).average().orElse(0) - wantCol)))
                .map(w -> w.stream().map(Seat::no).toList());
    }

    /** seat number -> holder, for every seat of the show that is held right now. Empty if Redis is down. */
    public Map<String, String> lockedSeats(long showId) {
        List<String> seatNos = jdbc.queryForList("SELECT seat_no FROM show_seats WHERE show_id = :show",
                Map.of("show", showId), String.class);
        Map<String, String> locked = new HashMap<>();
        try {
            List<String> owners = redis.opsForValue().multiGet(keys(showId, seatNos));
            for (int i = 0; owners != null && i < seatNos.size(); i++) {
                if (owners.get(i) != null) {
                    locked.put(seatNos.get(i), owners.get(i));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Couldn't read seat locks for show {}: {}", showId, e.getMessage());
        }
        return locked;
    }

    private static List<String> keys(long showId, List<String> seats) {
        return seats.stream().map(s -> "lock:show:" + showId + ":seat:" + s).toList();
    }
}
