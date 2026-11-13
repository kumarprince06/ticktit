package com.ticktit.movie.service;

import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.movie.dto.CatalogDtos.CityDto;
import com.ticktit.movie.dto.CatalogDtos.MovieCard;
import com.ticktit.movie.dto.CatalogDtos.MovieDetail;
import com.ticktit.movie.dto.CatalogDtos.Page;
import com.ticktit.movie.dto.CatalogDtos.SeatMap;
import com.ticktit.movie.dto.CatalogDtos.SeatView;
import com.ticktit.movie.dto.CatalogDtos.ShowInfo;
import com.ticktit.movie.dto.CatalogDtos.ShowSummary;
import com.ticktit.movie.dto.CatalogDtos.TheatreRef;
import com.ticktit.movie.entity.City;
import com.ticktit.movie.entity.Movie;
import com.ticktit.movie.entity.MovieStatus;
import com.ticktit.movie.repository.CityRepository;
import com.ticktit.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the movie catalogue: what's on, where and when, and the seat map of a show.
 * Plain SQL for the listing queries (filters, joins, counts); JPA for loading single rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final MovieRepository movieRepository;
    private final CityRepository cityRepository;

    public List<CityDto> cities() {
        return cityRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(c -> new CityDto(c.getSlug(), c.getName(), c.getState()))
                .toList();
    }

    /**
     * NOW_SHOWING: movies with an upcoming show in the city. COMING_SOON: not out yet, shown in every city.
     * Optional filters: language, genre, q (part of the title).
     */
    public Page<MovieCard> movies(String citySlug, MovieStatus status, String language, String genre, String q,
                                  int page, int size) {
        size = Math.clamp(size, 1, MAX_PAGE_SIZE);
        page = Math.max(page, 0);
        StringBuilder where = new StringBuilder(" WHERE m.status = :status");
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());

        if (status == MovieStatus.NOW_SHOWING && citySlug != null) {
            // text blocks drop leading spaces, so the separating space is added separately
            where.append(" ").append("""
                    AND EXISTS (SELECT 1 FROM shows s
                                 JOIN screens sc ON sc.id = s.screen_id
                                 JOIN theatres t ON t.id = sc.theatre_id
                                 JOIN cities c ON c.id = t.city_id
                                 WHERE s.movie_id = m.id AND c.slug = :city
                                   AND s.status = 'SCHEDULED' AND s.starts_at > now())""");
            params.addValue("city", citySlug);
        }
        if (language != null) {
            where.append(" AND :language = ANY(m.languages)");
            params.addValue("language", language);
        }
        if (genre != null) {
            where.append(" AND :genre = ANY(m.genres)");
            params.addValue("genre", genre);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND m.title ILIKE :q");
            params.addValue("q", "%" + q.trim().replace("%", "\\%").replace("_", "\\_") + "%");
        }

        long total = jdbc.queryForObject("SELECT count(*) FROM movies m" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<MovieCard> content = jdbc.query("""
                SELECT m.slug, m.title, m.tagline, m.certification, m.genres, m.languages, m.duration_min,
                       m.release_date, m.poster_url, m.status
                FROM movies m""" + where + """
                 ORDER BY CASE WHEN m.status = 'COMING_SOON' THEN m.release_date END ASC,
                          m.release_date DESC, m.title
                LIMIT :limit OFFSET :offset""", params, (rs, i) -> new MovieCard(
                rs.getString("slug"), rs.getString("title"), rs.getString("tagline"), rs.getString("certification"),
                strings(rs.getArray("genres")), strings(rs.getArray("languages")), rs.getInt("duration_min"),
                rs.getObject("release_date", LocalDate.class), rs.getString("poster_url"), rs.getString("status")));
        return new Page<>(content, page, size, total);
    }

    public MovieDetail movie(String slug) {
        Movie m = movieRepository.findBySlug(slug).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new MovieDetail(m.getSlug(), m.getTitle(), m.getTagline(), m.getSynopsis(), m.getCertification().name(),
                m.getGenres(), m.getLanguages(), m.getFormats(), m.getDurationMin(), m.getReleaseDate(),
                m.getCastMembers(), m.getPosterUrl(), m.getTrailerUrl(), m.getStatus().name());
    }

    /** Upcoming shows of a movie in a city on one local date, ordered by cinema then time. */
    public List<ShowSummary> shows(String movieSlug, String citySlug, LocalDate date) {
        City city = cityRepository.findBySlug(citySlug).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        ZoneId zone = ZoneId.of(city.getTimezone());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("movie", movieSlug)
                .addValue("city", city.getId())
                .addValue("from", Timestamp.from(date.atStartOfDay(zone).toInstant()))
                .addValue("to", Timestamp.from(date.plusDays(1).atStartOfDay(zone).toInstant()));
        return jdbc.query("""
                SELECT s.id, t.slug AS theatre_slug, t.name AS theatre_name, t.area, sc.name AS screen_name,
                       s.language, s.format, s.starts_at,
                       (SELECT count(*) FROM show_seats ss WHERE ss.show_id = s.id AND ss.status = 'AVAILABLE') AS available,
                       (SELECT count(*) FROM show_seats ss WHERE ss.show_id = s.id) AS total,
                       (SELECT string_agg(p.category || ':' || p.price_paise, ',') FROM show_prices p WHERE p.show_id = s.id) AS prices
                FROM shows s
                JOIN movies m ON m.id = s.movie_id
                JOIN screens sc ON sc.id = s.screen_id
                JOIN theatres t ON t.id = sc.theatre_id
                WHERE m.slug = :movie AND t.city_id = :city AND t.status = 'ACTIVE'
                  AND s.status = 'SCHEDULED' AND s.booking_opens_at <= now()
                  AND s.starts_at >= :from AND s.starts_at < :to AND s.starts_at > now()
                ORDER BY t.name, s.starts_at""", params, (rs, i) -> new ShowSummary(
                rs.getLong("id"),
                new TheatreRef(rs.getString("theatre_slug"), rs.getString("theatre_name"), rs.getString("area")),
                rs.getString("screen_name"), rs.getString("language"), rs.getString("format"),
                rs.getTimestamp("starts_at").toInstant(), prices(rs.getString("prices")),
                rs.getInt("available"), rs.getInt("total")));
    }

    /** Every seat of a show with its position, category, price and stored status. Locks are added by booking. */
    public SeatMap seatMap(long showId) {
        List<ShowInfo> info = jdbc.query("""
                SELECT s.id, m.slug, m.title, t.name AS theatre, t.area, c.name AS city, sc.name AS screen,
                       s.language, s.format, s.starts_at
                FROM shows s
                JOIN movies m ON m.id = s.movie_id
                JOIN screens sc ON sc.id = s.screen_id
                JOIN theatres t ON t.id = sc.theatre_id
                JOIN cities c ON c.id = t.city_id
                WHERE s.id = :id AND s.status = 'SCHEDULED'""", Map.of("id", showId), (rs, i) -> new ShowInfo(
                rs.getLong("id"), rs.getString("slug"), rs.getString("title"), rs.getString("theatre"),
                rs.getString("area"), rs.getString("city"), rs.getString("screen"), rs.getString("language"),
                rs.getString("format"), rs.getTimestamp("starts_at").toInstant()));
        if (info.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        Map<String, Long> prices = prices(jdbc.queryForObject(
                "SELECT string_agg(category || ':' || price_paise, ',') FROM show_prices WHERE show_id = :id",
                Map.of("id", showId), String.class));
        List<SeatView> seats = jdbc.query("""
                SELECT ss.seat_no, sl.row_label, sl.col_no, sl.grid_row, sl.grid_col, ss.category, ss.status
                FROM show_seats ss
                JOIN screen_seats sl ON sl.id = ss.screen_seat_id
                WHERE ss.show_id = :id
                ORDER BY sl.grid_row, sl.grid_col""", Map.of("id", showId), (rs, i) -> new SeatView(
                rs.getString("seat_no"), rs.getString("row_label"), rs.getInt("col_no"), rs.getInt("grid_row"),
                rs.getInt("grid_col"), rs.getString("category"),
                prices.getOrDefault(rs.getString("category"), 0L), rs.getString("status")));
        return new SeatMap(info.getFirst(), prices, seats);
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    /** "CLASSIC:18000,PRIME:28000" -> ordered map, cheapest category first. */
    private static Map<String, Long> prices(String packed) {
        Map<String, Long> prices = new LinkedHashMap<>();
        if (packed == null) {
            return prices;
        }
        Arrays.stream(packed.split(","))
                .map(p -> p.split(":"))
                .sorted((a, b) -> Long.compare(Long.parseLong(a[1]), Long.parseLong(b[1])))
                .forEach(p -> prices.put(p[0], Long.parseLong(p[1])));
        return prices;
    }
}
