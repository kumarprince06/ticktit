package com.ticktit.booking.repository;

import com.ticktit.booking.entity.BookingUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingUnitRepository extends JpaRepository<BookingUnit, Long> {

    List<BookingUnit> findByBookingId(long bookingId);
}
