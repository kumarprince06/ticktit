package com.ticktit.movie.repository;

import com.ticktit.movie.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByIsActiveTrueOrderByNameAsc();

    Optional<City> findBySlug(String slug);
}
