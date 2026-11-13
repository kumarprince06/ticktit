package com.ticktit.movie.dto;

import com.ticktit.movie.entity.CastMember;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Response bodies for the public catalogue API. Prices are in paise (ADR-009); the app formats them. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CityDto(String slug, String name, String state) {
    }

    public record MovieCard(String slug, String title, String tagline, String certification, List<String> genres,
                            List<String> languages, int durationMin, LocalDate releaseDate, String posterUrl,
                            String status) {
    }

    public record MovieDetail(String slug, String title, String tagline, String synopsis, String certification,
                              List<String> genres, List<String> languages, List<String> formats, int durationMin,
                              LocalDate releaseDate, List<CastMember> cast, String posterUrl, String trailerUrl,
                              String status) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements) {
    }

    public record TheatreRef(String slug, String name, String area) {
    }

    public record ShowSummary(long id, TheatreRef theatre, String screen, String language, String format,
                              Instant startsAt, Map<String, Long> prices, int availableSeats, int totalSeats) {
    }

    public record ShowInfo(long id, String movieSlug, String movieTitle, String theatre, String area, String city,
                           String screen, String language, String format, Instant startsAt) {
    }

    /** gridRow/gridCol place the seat on the drawn map, so aisles and gaps show. status: AVAILABLE/BOOKED/BLOCKED. */
    public record SeatView(String seatNo, String row, int col, int gridRow, int gridCol, String category,
                           long pricePaise, String status) {
    }

    public record SeatMap(ShowInfo show, Map<String, Long> prices, List<SeatView> seats) {
    }
}
