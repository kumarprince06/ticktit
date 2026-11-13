package com.ticktit.booking.repository;

import com.ticktit.booking.entity.Booking;
import com.ticktit.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPublicId(UUID publicId);

    /** The group's current attempt (LOCKED or PAYMENT_PENDING); at most one, by V4's unique index. */
    Optional<Booking> findFirstByGroupIdAndStatusIn(long groupId, List<BookingStatus> statuses);

    boolean existsByBookingCode(String code);

    /** Row lock for money changes: two payments landing together are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> lockById(long id);

    /** The group's latest attempt of any status (to show a confirmed or failed result). */
    Optional<Booking> findFirstByGroupIdOrderByCreatedAtDesc(long groupId);

    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.status = com.ticktit.booking.entity.BookingStatus.PAYMENT_PENDING AND b.paymentDeadline < :now""")
    List<Long> findOverduePayments(Instant now);

    /** Expiry job (ADR-013): locks past their 5 minutes become RELEASED. Bumps version so stale writers fail. */
    @Modifying
    @Query("""
            UPDATE Booking b SET b.status = com.ticktit.booking.entity.BookingStatus.RELEASED, b.version = b.version + 1
            WHERE b.status = com.ticktit.booking.entity.BookingStatus.LOCKED AND b.lockExpiresAt < :now""")
    int releaseExpiredLocks(Instant now);
}
