package com.ticktit.movie.service;

import com.ticktit.common.enums.ItemType;
import com.ticktit.common.inventory.InventoryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** The movie vertical's side of ADR-007: describes a show to the core (groups, later booking). */
@Component
@RequiredArgsConstructor
public class ShowInventory implements InventoryProvider {

    private final NamedParameterJdbcTemplate jdbc;

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

}
