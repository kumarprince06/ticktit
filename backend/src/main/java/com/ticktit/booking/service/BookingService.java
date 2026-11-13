package com.ticktit.booking.service;

import com.ticktit.auth.service.UserDirectory;
import com.ticktit.auth.service.UserDirectory.UserCard;
import com.ticktit.booking.dto.BookingDtos.AssignRequest;
import com.ticktit.booking.dto.BookingDtos.Assignment;
import com.ticktit.booking.dto.BookingDtos.BookingView;
import com.ticktit.booking.dto.BookingDtos.LockRequest;
import com.ticktit.booking.dto.BookingDtos.Person;
import com.ticktit.booking.dto.BookingDtos.SeatLine;
import com.ticktit.booking.dto.BookingDtos.Share;
import com.ticktit.booking.dto.BookingDtos.UnlockRequest;
import com.ticktit.booking.entity.Booking;
import com.ticktit.booking.entity.BookingStatus;
import com.ticktit.booking.entity.BookingUnit;
import com.ticktit.booking.repository.BookingRepository;
import com.ticktit.booking.repository.BookingUnitRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.enums.ItemType;
import com.ticktit.common.exception.ApiException;
import com.ticktit.common.idempotency.IdempotencyService;
import com.ticktit.common.inventory.InventoryProvider;
import com.ticktit.common.inventory.InventoryProvider.HoldResult;
import com.ticktit.common.inventory.InventoryProvider.UnitPrice;
import com.ticktit.group.GroupCancelledEvent;
import com.ticktit.group.entity.GroupStatus;
import com.ticktit.group.service.GroupService;
import com.ticktit.group.service.GroupService.GroupRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Seat locking for a group (architecture.md §4.1, PRD F3/F5/F11): the organiser holds seats for 5 minutes,
 * assigns them to members, and (next module) everyone pays. A single booking is a group of one.
 * Money-changing calls run through IdempotencyService (ADR-010).
 */
@Slf4j
@Service
public class BookingService {

    public static final Duration LOCK_TTL = Duration.ofMinutes(5);
    public static final int MAX_SEATS = 8;
    private static final List<BookingStatus> ACTIVE = List.of(BookingStatus.LOCKED, BookingStatus.PAYMENT_PENDING);
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final BookingUnitRepository unitRepository;
    private final GroupService groups;
    private final UserDirectory users;
    private final IdempotencyService idempotency;
    private final Map<ItemType, InventoryProvider> inventories = new EnumMap<>(ItemType.class);

    public BookingService(BookingRepository bookingRepository, BookingUnitRepository unitRepository, GroupService groups,
                          UserDirectory users, IdempotencyService idempotency, List<InventoryProvider> providers) {
        this.bookingRepository = bookingRepository;
        this.unitRepository = unitRepository;
        this.groups = groups;
        this.users = users;
        this.idempotency = idempotency;
        providers.forEach(p -> inventories.put(p.type(), p));
    }

    // ---------------------------------------------------------------- lock

    /**
     * Hold seats for the group for 5 minutes. Re-locking replaces the previous seats (the new ones are taken
     * first, so a failed re-lock keeps the old seats). 409 SEATS_UNAVAILABLE carries the taken seats and the
     * nearest free alternative.
     */
    @Transactional
    public BookingView lock(UUID callerId, String idempotencyKey, LockRequest request) {
        UserCard caller = users.requireActive(callerId);
        return idempotency.run(caller.id(), idempotencyKey, "POST /bookings/lock", request, BookingView.class,
                () -> doLock(caller, request));
    }

    private BookingView doLock(UserCard caller, LockRequest request) {
        List<String> seats = request.seats().stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().sorted(SEAT_ORDER).toList();
        if (seats.size() > MAX_SEATS) {
            throw new ApiException(ErrorCode.TOO_MANY_SEATS);
        }
        GroupRef group = groups.forBooking(request.groupId(), caller.id());
        requireLeader(group, caller);
        requireOpen(group);
        InventoryProvider inventory = inventory(group.itemType());
        if (!inventory.describe(group.itemId()).map(InventoryProvider.ItemSummary::bookable).orElse(false)) {
            throw new ApiException(ErrorCode.SHOW_NOT_BOOKABLE);
        }

        Optional<Booking> previous = bookingRepository.findFirstByGroupIdAndStatusIn(group.id(), ACTIVE);
        if (previous.isPresent() && previous.get().getStatus() == BookingStatus.PAYMENT_PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        List<String> previousSeats = previous.map(b -> seatsOf(b.getId())).orElse(List.of());
        String holder = group.publicId().toString();

        HoldResult hold = inventory.hold(group.itemId(), seats, holder, LOCK_TTL);
        if (!hold.held()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("unavailable", hold.unavailable());
            details.put("alternative", inventory.suggestAlternative(group.itemId(), seats).orElse(null));
            throw new ApiException(ErrorCode.SEATS_UNAVAILABLE, details);
        }
        // From here Redis holds the seats: give them back if the database work doesn't commit
        List<String> newlyHeld = seats.stream().filter(s -> !previousSeats.contains(s)).toList();
        List<String> noLongerWanted = previousSeats.stream().filter(s -> !seats.contains(s)).toList();
        afterCompletion(committed -> inventory.release(group.itemId(), committed ? noLongerWanted : newlyHeld, holder));

        previous.ifPresent(old -> {
            old.transitionTo(BookingStatus.RELEASED);
            bookingRepository.saveAndFlush(old);   // must leave "active" before the new attempt is inserted
        });

        Map<String, UnitPrice> prices = inventory.price(group.itemId(), seats);
        if (prices.size() != seats.size()) {
            throw new ApiException(ErrorCode.SEATS_UNAVAILABLE,
                    Map.of("unavailable", seats.stream().filter(s -> !prices.containsKey(s)).toList()));
        }

        Booking booking = new Booking();
        booking.setBookingCode(newCode());
        booking.setGroupId(group.id());
        booking.setItemType(group.itemType());
        booking.setItemId(group.itemId());
        booking.setLockExpiresAt(Instant.now().plus(LOCK_TTL));
        Map<String, Long> snapshot = new LinkedHashMap<>();
        prices.values().forEach(p -> snapshot.put(p.category(), p.pricePaise()));
        booking.setPriceSnapshot(snapshot);
        booking.setTotalPaise(prices.values().stream().mapToLong(p -> Pricing.payablePaise(p.pricePaise())).sum());
        bookingRepository.save(booking);

        // Default seating: one seat per member in join order (organiser first); extra seats go to the organiser
        List<BookingUnit> units = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            UnitPrice price = prices.get(seats.get(i));
            BookingUnit unit = new BookingUnit();
            unit.setBookingId(booking.getId());
            unit.setUnitRef(seats.get(i));
            unit.setCategory(price.category());
            unit.setAssigneeUserId(i < group.memberIds().size() ? group.memberIds().get(i) : group.leaderId());
            unit.setTicketPricePaise(price.pricePaise());
            unit.setTicketGstRateBp(Pricing.ticketGstRateBp(price.pricePaise()));
            unit.setTicketBasePaise(Pricing.basePaise(price.pricePaise()));
            unit.setTicketGstPaise(price.pricePaise() - unit.getTicketBasePaise());
            unit.setFeePaise(Pricing.FEE_PAISE);
            unit.setFeeGstPaise(Pricing.feeGstPaise());
            units.add(unit);
        }
        unitRepository.saveAll(units);
        return view(booking, units, group, caller);
    }

    // ---------------------------------------------------------------- unlock / assign / view

    /** Organiser gives the seats back. Nothing to do if there's no lock. Not allowed once payment has started. */
    @Transactional
    public void unlock(UUID callerId, String idempotencyKey, UnlockRequest request) {
        UserCard caller = users.requireActive(callerId);
        idempotency.run(caller.id(), idempotencyKey, "POST /bookings/unlock", request, Void.class, () -> {
            GroupRef group = groups.forBooking(request.groupId(), caller.id());
            requireLeader(group, caller);
            bookingRepository.findFirstByGroupIdAndStatusIn(group.id(), ACTIVE).ifPresent(b -> {
                if (b.getStatus() == BookingStatus.PAYMENT_PENDING) {
                    throw new ApiException(ErrorCode.PAYMENT_IN_PROGRESS);
                }
                release(b);
            });
            return null;
        });
    }

    /** Organiser decides who sits where. Every person named must be in the group. Only while LOCKED. */
    @Transactional
    public BookingView assign(UUID callerId, String idempotencyKey, UUID bookingId, AssignRequest request) {
        UserCard caller = users.requireActive(callerId);
        record Call(UUID bookingId, AssignRequest request) {
        }
        return idempotency.run(caller.id(), idempotencyKey, "POST /bookings/assign", new Call(bookingId, request),
                BookingView.class, () -> {
                    Booking booking = bookingRepository.findByPublicId(bookingId)
                            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
                    GroupRef group = groups.forBooking(groupPublicId(booking), caller.id());
                    requireLeader(group, caller);
                    if (booking.getStatus() != BookingStatus.LOCKED || booking.isLockExpired()) {
                        throw new ApiException(ErrorCode.BOOKING_NOT_LOCKED);
                    }
                    Map<String, BookingUnit> units = new HashMap<>();
                    unitRepository.findByBookingId(booking.getId()).forEach(u -> units.put(u.getUnitRef(), u));
                    for (Assignment a : request.assignments()) {
                        BookingUnit unit = units.get(a.seatNo().toUpperCase(Locale.ROOT));
                        long userId = users.byPublicId(a.userId()).map(UserCard::id).orElse(-1L);
                        if (unit == null || !group.memberIds().contains(userId)) {
                            throw new ApiException(ErrorCode.INVALID_ASSIGNMENT);
                        }
                        unit.setAssigneeUserId(userId);
                    }
                    return view(booking, new ArrayList<>(units.values()), group, caller);
                });
    }

    /** The group's current attempt, for members. Empty when there's none (or its lock just ran out). */
    @Transactional(readOnly = true)
    public Optional<BookingView> current(UUID callerId, UUID groupId) {
        UserCard caller = users.requireActive(callerId);
        GroupRef group = groups.forBooking(groupId, caller.id());
        return bookingRepository.findFirstByGroupIdAndStatusIn(group.id(), ACTIVE)
                .filter(b -> !b.isLockExpired())
                .map(b -> view(b, unitRepository.findByBookingId(b.getId()), group, caller));
    }

    /** Group cancelled by its organiser: let go of any seats it holds (runs in the cancel transaction). */
    @EventListener
    public void onGroupCancelled(GroupCancelledEvent event) {
        bookingRepository.findFirstByGroupIdAndStatusIn(event.groupId(), ACTIVE)
                .filter(b -> b.getStatus() == BookingStatus.LOCKED)
                .ifPresent(this::release);
    }

    // ---------------------------------------------------------------- helpers

    private void release(Booking booking) {
        booking.transitionTo(BookingStatus.RELEASED);
        List<String> seats = seatsOf(booking.getId());
        String holder = groupPublicId(booking).toString();
        InventoryProvider inventory = inventory(booking.getItemType());
        afterCompletion(committed -> {
            if (committed) {
                inventory.release(booking.getItemId(), seats, holder);
            }
        });
    }

    private BookingView view(Booking booking, List<BookingUnit> units, GroupRef group, UserCard caller) {
        List<BookingUnit> sorted = units.stream().sorted(Comparator.comparing(BookingUnit::getUnitRef, SEAT_ORDER)).toList();
        Map<Long, UserCard> people = users.byIds(group.memberIds());
        Map<Long, Share> shares = new LinkedHashMap<>();
        group.memberIds().forEach(id -> shares.put(id, new Share(person(people.get(id)), new ArrayList<>(), 0)));

        List<SeatLine> lines = new ArrayList<>();
        for (BookingUnit u : sorted) {
            // a member who left keeps nothing: their seat shows as unassigned until the organiser reassigns it
            Long assignee = u.getAssigneeUserId() != null && shares.containsKey(u.getAssigneeUserId()) ? u.getAssigneeUserId() : null;
            lines.add(new SeatLine(u.getUnitRef(), u.getCategory(), u.getTicketPricePaise(), u.payablePaise(),
                    assignee == null ? null : person(people.get(assignee))));
            if (assignee != null) {
                Share s = shares.get(assignee);
                s.seats().add(u.getUnitRef());
                shares.put(assignee, new Share(s.member(), s.seats(), s.sharePaise() + u.payablePaise()));
            }
        }
        return new BookingView(booking.getPublicId(), booking.getBookingCode(), booking.getStatus().name(),
                booking.getLockExpiresAt(), booking.getPaymentDeadline(), booking.getTotalPaise(),
                group.isLeader(caller.id()), lines, List.copyOf(shares.values()));
    }

    private static Person person(UserCard card) {
        return new Person(card.publicId(), card.name(), card.avatarUrl());
    }

    private UUID groupPublicId(Booking booking) {
        return groups.forBooking(booking.getGroupId()).publicId();
    }

    private List<String> seatsOf(long bookingId) {
        return unitRepository.findByBookingId(bookingId).stream().map(BookingUnit::getUnitRef).toList();
    }

    private static void requireLeader(GroupRef group, UserCard caller) {
        if (!group.isLeader(caller.id())) {
            throw new ApiException(ErrorCode.NOT_LEADER);
        }
    }

    private static void requireOpen(GroupRef group) {
        if (group.status() == GroupStatus.BOOKED) {
            throw new ApiException(ErrorCode.GROUP_BOOKED);
        }
        if (group.status() == GroupStatus.CANCELLED) {
            throw new ApiException(ErrorCode.GROUP_CANCELLED);
        }
    }

    private InventoryProvider inventory(ItemType type) {
        InventoryProvider provider = inventories.get(type);
        if (provider == null) {
            throw new IllegalStateException("No InventoryProvider for " + type);
        }
        return provider;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!bookingRepository.existsByBookingCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not find a free booking code");
    }

    /** Runs after the transaction ends: true if it committed, false if it rolled back. */
    private static void afterCompletion(java.util.function.Consumer<Boolean> action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status == STATUS_COMMITTED);
            }
        });
    }

    /** "A2" before "A10", rows in order. */
    static final Comparator<String> SEAT_ORDER = Comparator
            .comparing((String s) -> s.replaceAll("[0-9]", ""))
            .thenComparingInt(s -> Integer.parseInt(s.replaceAll("[^0-9]", "")));
}
