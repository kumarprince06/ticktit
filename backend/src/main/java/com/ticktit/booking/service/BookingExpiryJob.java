package com.ticktit.booking.service;

import com.ticktit.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * ADR-013: Redis frees the seats when a lock's 5 minutes run out; this marks the booking itself RELEASED.
 * Deadlines that move money (payment window) will be handled here too, from Postgres, never from Redis events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryJob {

    private final BookingRepository bookingRepository;

    // ponytail: every instance runs it; the update is conditional (status = LOCKED), so repeats are harmless
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    @Transactional
    public void releaseExpiredLocks() {
        int released = bookingRepository.releaseExpiredLocks(Instant.now());
        if (released > 0) {
            log.info("Released {} expired seat lock(s)", released);
        }
    }
}
