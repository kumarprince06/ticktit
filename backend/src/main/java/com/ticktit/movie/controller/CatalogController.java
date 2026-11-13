package com.ticktit.movie.controller;

import com.ticktit.movie.dto.CatalogDtos.CityDto;
import com.ticktit.movie.dto.CatalogDtos.MovieCard;
import com.ticktit.movie.dto.CatalogDtos.MovieDetail;
import com.ticktit.movie.dto.CatalogDtos.Page;
import com.ticktit.movie.dto.CatalogDtos.SeatMap;
import com.ticktit.movie.dto.CatalogDtos.ShowSummary;
import com.ticktit.movie.entity.MovieStatus;
import com.ticktit.movie.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Public catalogue: no sign-in needed (SecurityConfig permits GET on these paths). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/cities")
    public List<CityDto> cities() {
        return catalogService.cities();
    }

    /** e.g. /movies?city=siliguri&status=NOW_SHOWING&language=Hindi&genre=Drama&q=kite&page=0&size=20 */
    @GetMapping("/movies")
    public Page<MovieCard> movies(@RequestParam(required = false) String city,
                                  @RequestParam(defaultValue = "NOW_SHOWING") MovieStatus status,
                                  @RequestParam(required = false) String language,
                                  @RequestParam(required = false) String genre,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return catalogService.movies(city, status, language, genre, q, page, size);
    }

    @GetMapping("/movies/{slug}")
    public MovieDetail movie(@PathVariable String slug) {
        return catalogService.movie(slug);
    }

    /** e.g. /shows?movie=paper-kites&city=siliguri&date=2026-11-14 */
    @GetMapping("/shows")
    public List<ShowSummary> shows(@RequestParam String movie,
                                   @RequestParam String city,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return catalogService.shows(movie, city, date);
    }

    @GetMapping("/shows/{id}/seats")
    public SeatMap seats(@PathVariable long id) {
        return catalogService.seatMap(id);
    }
}
