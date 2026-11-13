package com.ticktit.movie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty database with a fictional catalogue (cities, cinemas, seat layouts, movies) and keeps
 * shows scheduled for the next 7 days. Local development only: app.demo-data.enabled=false in prod.
 * Idempotent: safe to run on every start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int DAYS_AHEAD = 7;
    private static final LocalTime[] SHOW_TIMES = {LocalTime.of(10, 0), LocalTime.of(13, 30), LocalTime.of(17, 0), LocalTime.of(20, 30)};

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (jdbc.queryForObject("SELECT count(*) FROM cities", Long.class) == 0) {
            seedCatalogue();
        }
        // movies whose release date has come move to NOW_SHOWING
        jdbc.update("UPDATE movies SET status = 'NOW_SHOWING' WHERE status = 'COMING_SOON' AND release_date <= ?",
                LocalDate.now(IST));
        int created = scheduleShows();
        if (created > 0) {
            log.info("Demo data: scheduled {} shows for the next {} days", created, DAYS_AHEAD);
        }
    }

    // ---------------------------------------------------------------- one-time catalogue

    private void seedCatalogue() {
        String[][] cities = {
                {"Siliguri", "siliguri", "West Bengal", "19", "City Centre, Uttorayon", "Sevoke Road"},
                {"Kolkata", "kolkata", "West Bengal", "19", "Salt Lake Sector V", "Park Street"},
                {"Bengaluru", "bengaluru", "Karnataka", "29", "Indiranagar", "Koramangala"},
                {"Mumbai", "mumbai", "Maharashtra", "27", "Andheri West", "Lower Parel"},
                {"Delhi NCR", "delhi-ncr", "Delhi", "07", "Saket", "Connaught Place"},
                {"Hyderabad", "hyderabad", "Telangana", "36", "Banjara Hills", "Gachibowli"},
        };
        for (String[] c : cities) {
            Long cityId = jdbc.queryForObject(
                    "INSERT INTO cities (name, slug, state, state_code) VALUES (?, ?, ?, ?) RETURNING id",
                    Long.class, c[0], c[1], c[2], c[3]);
            addTheatre(cityId, "Aurora Cinemas", "aurora-cinemas", c[4], true);
            addTheatre(cityId, "Lumière Screens", "lumiere-screens", c[5], false);
        }
        addMovies();
        log.info("Demo data: created {} cities with cinemas, seat layouts and movies", cities.length);
    }

    private void addTheatre(Long cityId, String name, String slug, String area, boolean hasImax) {
        Long theatreId = jdbc.queryForObject("""
                INSERT INTO theatres (city_id, name, slug, address_line, area, pincode, amenities)
                VALUES (?, ?, ?, ?, ?, ?, '{PARKING,FOOD,M_TICKET,WHEELCHAIR}') RETURNING id""",
                Long.class, cityId, name, slug, "1st floor, " + area, area, "700001");
        addScreen(theatreId, "Audi 1", "STANDARD");
        addScreen(theatreId, "Audi 2", hasImax ? "IMAX" : "STANDARD");
    }

    /**
     * The same layout everywhere: 1 recliner row, 4 prime rows, 5 classic rows (146 seats), with a centre aisle
     * and an empty grid row between categories. Row A is the back row (furthest from the screen).
     */
    private void addScreen(Long theatreId, String name, String type) {
        record Block(String category, String rows, int seats) {
        }
        List<Block> blocks = List.of(new Block("RECLINER", "A", 10), new Block("PRIME", "BCDE", 14),
                new Block("CLASSIC", "FGHJK", 16));
        int widest = 16 + 1;   // seats + aisle
        List<Object[]> seats = new ArrayList<>();
        int gridRow = 0;
        for (Block block : blocks) {
            for (char row : block.rows().toCharArray()) {
                int offset = (widest - (block.seats() + 1)) / 2;
                for (int n = 1; n <= block.seats(); n++) {
                    int gridCol = offset + n + (n > block.seats() / 2 ? 1 : 0);   // +1 skips the aisle
                    seats.add(new Object[]{row + String.valueOf(n), String.valueOf(row), n, gridRow, gridCol, block.category()});
                }
                gridRow++;
            }
            gridRow++;   // gap between categories
        }
        Long screenId = jdbc.queryForObject(
                "INSERT INTO screens (theatre_id, name, screen_type, sound, total_seats) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, theatreId, name, type, type.equals("IMAX") ? "DOLBY_ATMOS" : "DOLBY_7_1", seats.size());
        jdbc.batchUpdate("""
                INSERT INTO screen_seats (screen_id, seat_no, row_label, col_no, grid_row, grid_col, category)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                seats.stream().map(s -> new Object[]{screenId, s[0], s[1], s[2], s[3], s[4], s[5]}).toList());
    }

    private void addMovies() {
        Object[][] movies = {
                {"monsoon-heist", "Monsoon Heist", "Seven strangers. One flooded vault.", "When the rains shut down the city for three days, a retired locksmith gathers an unlikely crew to empty a bank vault that is slowly filling with water.", "UA13", 142, "2026-10-31", "{Hindi,English}", "{2D,IMAX_2D}", "{Thriller,Crime}", "[{\"name\":\"Arnav Sethi\",\"role\":\"ACTOR\",\"character\":\"Dev\"},{\"name\":\"Mira Kapoor\",\"role\":\"ACTOR\",\"character\":\"Tara\"},{\"name\":\"Devika Rao\",\"role\":\"DIRECTOR\",\"character\":null}]"},
                {"paper-kites", "Paper Kites", "Some friendships never land.", "Three childhood friends reunite on a Kolkata rooftop for the kite festival that once tore them apart.", "U", 128, "2026-11-07", "{Bengali,Hindi}", "{2D}", "{Drama,Family}", "[{\"name\":\"Ishaan Bose\",\"role\":\"ACTOR\",\"character\":\"Rono\"},{\"name\":\"Ritika Sen\",\"role\":\"ACTOR\",\"character\":\"Mitu\"},{\"name\":\"Amol Ghosh\",\"role\":\"DIRECTOR\",\"character\":null}]"},
                {"neon-ghats", "Neon Ghats", "The river remembers everything.", "In a near-future Varanasi lit by holograms, a detective follows a string of disappearances back to the river's edge.", "UA16", 151, "2026-10-24", "{Hindi,Tamil,Telugu}", "{2D,IMAX_2D}", "{Mystery,Sci-Fi}", "[{\"name\":\"Kabir Malhotra\",\"role\":\"ACTOR\",\"character\":\"Inspector Rudra\"},{\"name\":\"Ananya Iyer\",\"role\":\"ACTOR\",\"character\":\"Gauri\"}]"},
                {"the-ninth-over", "The Ninth Over", "One over to change a life.", "A small-town bowler gets one over in a national final, and a lifetime of doubt to bowl through.", "U", 136, "2026-11-01", "{Hindi,English}", "{2D}", "{Sports,Drama}", "[{\"name\":\"Rohan Verma\",\"role\":\"ACTOR\",\"character\":\"Bittu\"},{\"name\":\"Sana Qureshi\",\"role\":\"ACTOR\",\"character\":\"Coach Naaz\"},{\"name\":\"Vikram Joshi\",\"role\":\"DIRECTOR\",\"character\":null}]"},
                {"saffron-skies", "Saffron Skies", "Love at thirty thousand feet.", "A pilot and a nervous flyer keep ending up on the same delayed flight, until they stop calling it luck.", "UA7", 118, "2026-11-14", "{Hindi}", "{2D}", "{Romance,Comedy}", "[{\"name\":\"Aditi Nair\",\"role\":\"ACTOR\",\"character\":\"Captain Ira\"},{\"name\":\"Karan Bedi\",\"role\":\"ACTOR\",\"character\":\"Neel\"}]"},
                {"echoes-of-the-hills", "Echoes of the Hills", "A song that waited 40 years.", "In the tea gardens near Darjeeling, a young singer finds an unfinished song written by her grandmother.", "U", 124, "2026-10-17", "{Nepali,Hindi}", "{2D}", "{Musical,Drama}", "[{\"name\":\"Pema Lama\",\"role\":\"ACTOR\",\"character\":\"Dolma\"},{\"name\":\"Suraj Thapa\",\"role\":\"MUSIC\",\"character\":null}]"},
                {"glass-tiger", "Glass Tiger", "Fragile. Fearless.", "A stunt double with brittle bones takes on the role of a lifetime, on and off the set.", "UA16", 158, "2026-12-05", "{Hindi,Tamil,Telugu,Kannada}", "{2D,IMAX_2D}", "{Action,Adventure}", "[{\"name\":\"Dev Raghavan\",\"role\":\"ACTOR\",\"character\":\"Arjun\"},{\"name\":\"Nisha Pillai\",\"role\":\"ACTOR\",\"character\":\"Meera\"}]"},
                {"small-hours", "Small Hours", "Nothing good happens after 2 a.m. Except this.", "Four night-shift workers at a 24-hour pharmacy accidentally become the city's most-wanted.", "UA13", 109, "2026-12-12", "{English,Hindi}", "{2D}", "{Comedy}", "[{\"name\":\"Tara Menon\",\"role\":\"ACTOR\",\"character\":\"Rosh\"},{\"name\":\"Yusuf Khan\",\"role\":\"ACTOR\",\"character\":\"Bilal\"},{\"name\":\"Leela D'Souza\",\"role\":\"ACTOR\",\"character\":\"Ammu\"}]"},
        };
        LocalDate today = LocalDate.now(IST);
        for (Object[] m : movies) {
            LocalDate release = LocalDate.parse((String) m[6]);
            jdbc.update("""
                    INSERT INTO movies (slug, title, tagline, synopsis, certification, duration_min, release_date,
                                        languages, formats, genres, cast_members, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[], ?::jsonb, ?)""",
                    m[0], m[1], m[2], m[3], m[4], m[5], release, m[7], m[8], m[9], m[10],
                    release.isAfter(today) ? "COMING_SOON" : "NOW_SHOWING");
        }
    }

    // ---------------------------------------------------------------- rolling show schedule

    /**
     * For each screen and each of the next 7 days without shows: 4 shows, rotating through the NOW_SHOWING movies.
     * Seats are copied from the screen layout in one INSERT ... SELECT per show.
     */
    private int scheduleShows() {
        List<Long> movieIds = jdbc.queryForList(
                "SELECT id FROM movies WHERE status = 'NOW_SHOWING' ORDER BY release_date DESC, id", Long.class);
        if (movieIds.isEmpty()) {
            return 0;
        }
        List<java.util.Map<String, Object>> screens = jdbc.queryForList("SELECT id, screen_type FROM screens ORDER BY id");
        LocalDate today = LocalDate.now(IST);
        int created = 0;
        for (int day = 0; day < DAYS_AHEAD; day++) {
            LocalDate date = today.plusDays(day);
            for (java.util.Map<String, Object> screen : screens) {
                long screenId = ((Number) screen.get("id")).longValue();
                boolean imax = "IMAX".equals(screen.get("screen_type"));
                ZonedDateTime dayStart = date.atStartOfDay(IST);
                Long existing = jdbc.queryForObject(
                        "SELECT count(*) FROM shows WHERE screen_id = ? AND starts_at >= ? AND starts_at < ?",
                        Long.class, screenId, java.sql.Timestamp.from(dayStart.toInstant()),
                        java.sql.Timestamp.from(dayStart.plusDays(1).toInstant()));
                if (existing > 0) {
                    continue;
                }
                for (int slot = 0; slot < SHOW_TIMES.length; slot++) {
                    long movieId = movieIds.get((int) ((screenId + slot + day) % movieIds.size()));
                    createShow(movieId, screenId, imax, date.atTime(SHOW_TIMES[slot]).atZone(IST));
                    created++;
                }
            }
        }
        return created;
    }

    private void createShow(long movieId, long screenId, boolean imax, ZonedDateTime startsAt) {
        java.util.Map<String, Object> movie = jdbc.queryForMap(
                "SELECT duration_min, languages[1] AS language FROM movies WHERE id = ?", movieId);
        int minutes = ((Number) movie.get("duration_min")).intValue() + 20;   // + ads and cleaning
        Long showId = jdbc.queryForObject("""
                INSERT INTO shows (movie_id, screen_id, language, format, starts_at, ends_at, booking_opens_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                movieId, screenId, movie.get("language"), imax ? "IMAX 2D" : "2D",
                java.sql.Timestamp.from(startsAt.toInstant()),
                java.sql.Timestamp.from(startsAt.plusMinutes(minutes).toInstant()),
                java.sql.Timestamp.from(startsAt.minusDays(14).toInstant()));
        long[] prices = imax ? new long[]{32000, 42000, 60000} : new long[]{18000, 28000, 45000};
        jdbc.update("""
                INSERT INTO show_prices (show_id, category, price_paise)
                VALUES (?, 'CLASSIC', ?), (?, 'PRIME', ?), (?, 'RECLINER', ?)""",
                showId, prices[0], showId, prices[1], showId, prices[2]);
        jdbc.update("""
                INSERT INTO show_seats (show_id, screen_seat_id, seat_no, category, status)
                SELECT ?, id, seat_no, category, CASE WHEN is_active THEN 'AVAILABLE' ELSE 'BLOCKED' END
                FROM screen_seats WHERE screen_id = ?""", showId, screenId);
    }
}
