package com.ticktit.booking.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.booking.dto.BookingDtos.AssignRequest;
import com.ticktit.booking.dto.BookingDtos.Assignment;
import com.ticktit.booking.dto.BookingDtos.BookingView;
import com.ticktit.booking.dto.BookingDtos.LockRequest;
import com.ticktit.booking.dto.BookingDtos.UnlockRequest;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.group.service.GroupService;
import com.ticktit.movie.service.CatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real Postgres + Redis with a demo show. Test users are booking-test-*@example.com; everything is removed after. */
@SpringBootTest
class BookingServiceTest {

    @Autowired BookingService bookings;
    @Autowired GroupService groups;
    @Autowired CatalogService catalog;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    long showId;
    UUID asha, rahul;
    UUID ashaGroup;

    @BeforeEach
    void setUp() {
        showId = catalog.shows("paper-kites", "kolkata", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2)).getFirst().id();
        clearLocks();
        asha = user("asha");
        rahul = user("rahul");
        ashaGroup = groups.create(asha, showId).groupId();
        groups.join(rahul, groups.view(asha, ashaGroup).code());
    }

    @AfterEach
    void cleanUp() {
        clearLocks();
        String testUsers = "SELECT id FROM users WHERE email LIKE 'booking-test-%'";
        jdbc.update("DELETE FROM bookings WHERE group_id IN (SELECT id FROM groups WHERE leader_id IN (" + testUsers + "))");
        jdbc.update("DELETE FROM groups WHERE leader_id IN (" + testUsers + ")");
        jdbc.update("DELETE FROM users WHERE email LIKE 'booking-test-%'");
    }

    @Test
    void lockHoldsTheSeatsAndSeatsEachMemberInJoinOrder() {
        BookingView view = bookings.lock(asha, key(), new LockRequest(ashaGroup, List.of("F7", "f8")));

        assertEquals("LOCKED", view.status());
        assertEquals(List.of("F7", "F8"), view.seats().stream().map(s -> s.seatNo()).toList());
        assertEquals("Asha Test", view.seats().get(0).assignee().name());
        assertEquals("Rahul Test", view.seats().get(1).assignee().name());
        assertEquals(ashaGroup.toString(), redis.opsForValue().get("lock:show:" + showId + ":seat:F7"));
        var map = catalog.seatMap(showId, ashaGroup.toString());
        assertTrue(map.seats().stream().filter(s -> s.seatNo().equals("F7")).findFirst().orElseThrow().heldByYou());
    }

    @Test
    void priceSplitMatchesPaymentMd() {
        // PAYMENT.md: a ₹250 ticket = 21186 base + 3814 GST; + ₹20 fee + ₹3.60 GST = 27360 payable
        assertEquals(21186, Pricing.basePaise(25000));
        assertEquals(27360, Pricing.payablePaise(25000));
        assertEquals(500, Pricing.ticketGstRateBp(10000));    // ₹100 or less: 5%
        assertEquals(1800, Pricing.ticketGstRateBp(10001));
    }

    @Test
    void anotherGroupIsRefusedAndOfferedAnAlternative() {
        bookings.lock(asha, key(), new LockRequest(ashaGroup, List.of("G5", "G6")));
        UUID other = user("other");
        UUID otherGroup = groups.create(other, showId).groupId();

        ApiException ex = assertThrows(ApiException.class,
                () -> bookings.lock(other, key(), new LockRequest(otherGroup, List.of("G6", "G7"))));
        assertEquals(ErrorCode.SEATS_UNAVAILABLE, ex.getErrorCode());
        assertEquals(List.of("G6"), ex.getDetails().get("unavailable"));
        @SuppressWarnings("unchecked") List<String> alternative = (List<String>) ex.getDetails().get("alternative");
        assertEquals(2, alternative.size());
        assertFalse(alternative.contains("G5") || alternative.contains("G6"));
    }

    @Test
    void tenGroupsRacingForOneSeatProduceExactlyOneLock() throws Exception {
        List<UUID[]> racers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID leader = user("racer" + i);
            racers.add(new UUID[]{leader, groups.create(leader, showId).groupId()});
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UUID[] r : racers) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    bookings.lock(r[0], key(), new LockRequest(r[1], List.of("H9")));
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        start.countDown();   // everyone at once
        int winners = 0;
        for (Future<Boolean> f : results) {
            winners += f.get(30, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(1, winners);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM booking_units u JOIN bookings b ON b.id = u.booking_id
                WHERE b.item_id = ? AND b.status = 'LOCKED' AND u.unit_ref = 'H9'""", Integer.class, showId));
    }

    @Test
    void relockSwapsSeatsAndFreesTheOldOnes() {
        bookings.lock(asha, key(), new LockRequest(ashaGroup, List.of("J1", "J2")));
        bookings.lock(asha, key(), new LockRequest(ashaGroup, List.of("J2", "J3")));

        assertNull(redis.opsForValue().get("lock:show:" + showId + ":seat:J1"));
        assertNotNull(redis.opsForValue().get("lock:show:" + showId + ":seat:J3"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM bookings b JOIN groups g ON g.id = b.group_id WHERE g.public_id = ? AND b.status = 'LOCKED'", Integer.class, ashaGroup));
    }

    @Test
    void unlockFreesTheSeats() {
        bookings.lock(asha, key(), new LockRequest(ashaGroup, List.of("K3")));
        bookings.unlock(asha, key(), new UnlockRequest(ashaGroup));

        assertNull(redis.opsForValue().get("lock:show:" + showId + ":seat:K3"));
        assertTrue(bookings.current(asha, ashaGroup).isEmpty());
    }

    @Test
    void onlyTheOrganiserLocksAndSeatsGoOnlyToMembers() {
        ApiException notLeader = assertThrows(ApiException.class,
                () -> bookings.lock(rahul, key(), new LockRequest(ashaGroup, List.of("F1"))));
        assertEquals(ErrorCode.NOT_LEADER, notLeader.getErrorCode());

        BookingView view = bookings.lock(asha, key(), new LockRequest(ashaGroup, List.of("F1", "F2")));
        BookingView reassigned = bookings.assign(asha, key(), view.id(), new AssignRequest(List.of(new Assignment("F1", rahul))));
        assertEquals("Rahul Test", reassigned.seats().getFirst().assignee().name());

        UUID stranger = user("stranger");
        ApiException bad = assertThrows(ApiException.class,
                () -> bookings.assign(asha, key(), view.id(), new AssignRequest(List.of(new Assignment("F1", stranger)))));
        assertEquals(ErrorCode.INVALID_ASSIGNMENT, bad.getErrorCode());
    }

    @Test
    void tooManySeatsAreRefused() {
        List<String> nine = List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9");
        assertEquals(ErrorCode.TOO_MANY_SEATS,
                assertThrows(ApiException.class, () -> bookings.lock(asha, key(), new LockRequest(ashaGroup, nine))).getErrorCode());
    }

    // ---------------------------------------------------------------- idempotency (ADR-010)

    @Test
    void sameKeyReplaysTheFirstAnswerInsteadOfLockingAgain() {
        String key = key();
        BookingView first = bookings.lock(asha, key, new LockRequest(ashaGroup, List.of("F3")));
        BookingView again = bookings.lock(asha, key, new LockRequest(ashaGroup, List.of("F3")));   // double-click

        assertEquals(first.id(), again.id());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM bookings b JOIN groups g ON g.id = b.group_id WHERE g.public_id = ?", Integer.class, ashaGroup));
    }

    @Test
    void sameKeyWithADifferentRequestIsRejected() {
        String key = key();
        bookings.lock(asha, key, new LockRequest(ashaGroup, List.of("F4")));
        ApiException ex = assertThrows(ApiException.class, () -> bookings.lock(asha, key, new LockRequest(ashaGroup, List.of("F5"))));
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, ex.getErrorCode());
    }

    @Test
    void aFailedAttemptDoesNotUseUpTheKey() {
        UUID other = user("other2");
        UUID otherGroup = groups.create(other, showId).groupId();
        bookings.lock(other, key(), new LockRequest(otherGroup, List.of("F6")));

        String key = key();
        assertThrows(ApiException.class, () -> bookings.lock(asha, key, new LockRequest(ashaGroup, List.of("F6"))));   // taken
        bookings.unlock(other, key(), new UnlockRequest(otherGroup));                                               // freed
        assertEquals("LOCKED", bookings.lock(asha, key, new LockRequest(ashaGroup, List.of("F6"))).status());     // retry works
    }

    @Test
    void missingKeyIsRefused() {
        ApiException ex = assertThrows(ApiException.class, () -> bookings.lock(asha, null, new LockRequest(ashaGroup, List.of("F1"))));
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, ex.getErrorCode());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private void clearLocks() {
        var keys = redis.keys("lock:show:" + showId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private UUID user(String name) {
        User u = new User();
        u.setName(name.substring(0, 1).toUpperCase() + name.substring(1) + " Test");
        u.setEmail("booking-test-" + name + "@example.com");
        u.setPasswordHash("hash");
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerifiedAt(Instant.now());
        return userRepository.save(u).getPublicId();
    }

}
