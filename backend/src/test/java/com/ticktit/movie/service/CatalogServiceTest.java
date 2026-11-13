package com.ticktit.movie.service;

import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.movie.dto.CatalogDtos.MovieCard;
import com.ticktit.movie.dto.CatalogDtos.SeatMap;
import com.ticktit.movie.dto.CatalogDtos.SeatView;
import com.ticktit.movie.dto.CatalogDtos.ShowSummary;
import com.ticktit.movie.entity.MovieStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reads the demo catalogue that DemoDataSeeder puts in the local database (app.demo-data.enabled=true). */
@SpringBootTest
class CatalogServiceTest {

    @Autowired CatalogService catalog;

    private static LocalDate tomorrow() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
    }

    @Test
    void citiesAreListed() {
        assertTrue(catalog.cities().stream().anyMatch(c -> c.slug().equals("siliguri")));
    }

    @Test
    void nowShowingOnlyHasReleasedMoviesWithShowsInTheCity() {
        List<MovieCard> movies = catalog.movies("siliguri", MovieStatus.NOW_SHOWING, null, null, null, 0, 20).content();

        assertFalse(movies.isEmpty());
        assertTrue(movies.stream().allMatch(m -> m.status().equals("NOW_SHOWING")));
        assertTrue(movies.stream().noneMatch(m -> m.releaseDate().isAfter(LocalDate.now(ZoneId.of("Asia/Kolkata")))));
    }

    @Test
    void filtersNarrowTheList() {
        List<MovieCard> drama = catalog.movies("siliguri", MovieStatus.NOW_SHOWING, null, "Drama", null, 0, 20).content();
        List<MovieCard> byTitle = catalog.movies(null, MovieStatus.NOW_SHOWING, null, null, "KITE", 0, 20).content();

        assertTrue(drama.stream().allMatch(m -> m.genres().contains("Drama")));
        assertEquals(List.of("Paper Kites"), byTitle.stream().map(MovieCard::title).toList());   // case-insensitive
    }

    @Test
    void pagingReportsTheTotal() {
        var page = catalog.movies("siliguri", MovieStatus.NOW_SHOWING, null, null, null, 0, 2);
        assertEquals(2, page.content().size());
        assertTrue(page.totalElements() > 2);
    }

    @Test
    void showsHavePricesAndAFullSeatCount() {
        List<ShowSummary> shows = catalog.shows("paper-kites", "siliguri", tomorrow());

        assertFalse(shows.isEmpty());
        ShowSummary show = shows.getFirst();
        assertEquals(List.of("CLASSIC", "PRIME", "RECLINER"), List.copyOf(show.prices().keySet()));   // cheapest first
        assertEquals(146, show.totalSeats());
    }

    @Test
    void seatMapHasEverySeatWithItsPriceAndAnAisle() {
        long showId = catalog.shows("paper-kites", "siliguri", tomorrow()).getFirst().id();
        SeatMap map = catalog.seatMap(showId);

        assertEquals(146, map.seats().size());
        SeatView a5 = find(map, "A5");
        SeatView a6 = find(map, "A6");
        assertEquals("RECLINER", a5.category());
        assertEquals(map.prices().get("RECLINER"), a5.pricePaise());
        assertEquals(2, a6.gridCol() - a5.gridCol(), "an aisle column sits between A5 and A6");
    }

    @Test
    void unknownMovieOrShowIs404() {
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class, () -> catalog.movie("nope")).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class, () -> catalog.seatMap(-1)).getErrorCode());
    }

    private static SeatView find(SeatMap map, String seatNo) {
        return map.seats().stream().filter(s -> s.seatNo().equals(seatNo)).findFirst().orElseThrow();
    }
}
