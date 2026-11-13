package com.ticktit.booking.service;

import com.ticktit.auth.service.UserDirectory;
import com.ticktit.booking.entity.Booking;
import com.ticktit.booking.entity.BookingStatus;
import com.ticktit.booking.entity.BookingUnit;
import com.ticktit.booking.entity.PaymentMode;
import com.ticktit.booking.repository.BookingRepository;
import com.ticktit.booking.repository.BookingUnitRepository;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.enums.ItemType;
import com.ticktit.common.exception.ApiException;
import com.ticktit.common.inventory.InventoryProvider;
import com.ticktit.group.service.GroupService;
import com.ticktit.group.service.GroupService.GroupRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The booking module's money-facing state changes (architecture.md §3-4), used by the payment module:
 * open the payment window, record money against seats, confirm, fail. Each runs in the caller's transaction
 * with the booking row locked, so concurrent payments are applied one at a time.
 */
@Slf4j
@Service
public class BookingLifecycle {

    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(10);
    /** Seats stay held a little past the deadline so a last-second payment still finds them (architecture.md §4.2). */
    private static final Duration HOLD_GRACE = Duration.ofSeconds(60);

    private final BookingRepository bookingRepository;
    private final BookingUnitRepository unitRepository;
    private final GroupService groups;
    private final UserDirectory users;
    private final InvoiceNumbers invoiceNumbers;
    private final Map<ItemType, InventoryProvider> inventories = new EnumMap<>(ItemType.class);

    public BookingLifecycle(BookingRepository bookingRepository, BookingUnitRepository unitRepository, GroupService groups,
                            UserDirectory users, InvoiceNumbers invoiceNumbers, List<InventoryProvider> providers) {
        this.bookingRepository = bookingRepository;
        this.unitRepository = unitRepository;
        this.groups = groups;
        this.users = users;
        this.invoiceNumbers = invoiceNumbers;
        providers.forEach(p -> inventories.put(p.type(), p));
    }

    /** What the payment module may know about a booking. Mutate it only through this class. */
    public record PayTarget(Booking booking, GroupRef group, List<BookingUnit> units) {
        public long bookingId() {
            return booking.getId();
        }

        /** Unpaid seats of this person (Split Pay share still owed). */
        public long ownShareLeft(long userId) {
            return units.stream().filter(u -> u.getPaidAt() == null && Long.valueOf(userId).equals(u.getAssigneeUserId()))
                    .mapToLong(BookingUnit::payablePaise).sum();
        }

        public long everythingLeft() {
            return units.stream().filter(u -> u.getPaidAt() == null).mapToLong(BookingUnit::payablePaise).sum();
        }
    }

    /** Thrown when the final seat guard finds a seat already sold. The caller must roll back, then fail and refund. */
    public static class SeatConflictException extends RuntimeException {
        public SeatConflictException(String message) {
            super(message);
        }
    }

    public enum Outcome { STILL_PENDING, CONFIRMED, NOT_PAYABLE }

    /** Locks the booking row; 404 unless the caller is in the booking's group. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PayTarget loadForPayment(UUID bookingPublicId, long callerId) {
        Booking found = bookingRepository.findByPublicId(bookingPublicId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        GroupRef group = groups.forBooking(groups.forBooking(found.getGroupId()).publicId(), callerId);
        Booking booking = bookingRepository.lockById(found.getId()).orElseThrow();
        return new PayTarget(booking, group, unitRepository.findByBookingId(booking.getId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PayTarget lockForSettlement(long bookingId) {
        Booking booking = bookingRepository.lockById(bookingId).orElseThrow();
        return new PayTarget(booking, groups.forBooking(booking.getGroupId()), unitRepository.findByBookingId(bookingId));
    }

    /** LOCKED -> PAYMENT_PENDING with a 10-minute deadline; seats stay held until just after it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void startPaymentWindow(PayTarget target, PaymentMode mode) {
        Booking booking = target.booking();
        if (booking.getStatus() != BookingStatus.LOCKED || booking.isLockExpired()) {
            throw new ApiException(ErrorCode.BOOKING_NOT_LOCKED);
        }
        if (mode == PaymentMode.SPLIT) {
            boolean everySeatHasSomeone = target.units().stream()
                    .allMatch(u -> u.getAssigneeUserId() != null && target.group().memberIds().contains(u.getAssigneeUserId()));
            boolean everyoneHasASeat = target.group().memberIds().stream()
                    .allMatch(m -> target.units().stream().anyMatch(u -> m.equals(u.getAssigneeUserId())));
            if (!everySeatHasSomeone || !everyoneHasASeat) {
                throw new ApiException(ErrorCode.SEATS_NOT_ASSIGNED);
            }
        }
        Instant deadline = Instant.now().plus(PAYMENT_WINDOW);
        booking.transitionTo(BookingStatus.PAYMENT_PENDING);
        booking.setMode(mode);
        booking.setPaymentDeadline(deadline);

        InventoryProvider inventory = inventory(booking);
        List<String> seats = target.units().stream().map(BookingUnit::getUnitRef).toList();
        var extended = inventory.hold(booking.getItemId(), seats, target.group().publicId().toString(),
                Duration.between(Instant.now(), deadline.plus(HOLD_GRACE)));
        if (!extended.held()) {   // only possible if the lock expired between checks
            throw new ApiException(ErrorCode.BOOKING_NOT_LOCKED);
        }
    }

    /**
     * Money arrived. Marks the seats it pays for, adds it to the booking, and confirms when everything is paid.
     * NOT_PAYABLE (refund it) when the booking is no longer waiting for money, or the amount doesn't match what
     * is still owed (e.g. the organiser already paid for everyone).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome recordPayment(PayTarget target, long payerId, boolean coversEverything, long amountPaise) {
        Booking booking = target.booking();
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            return Outcome.NOT_PAYABLE;
        }
        long due = coversEverything ? target.everythingLeft() : target.ownShareLeft(payerId);
        if (due == 0 || due != amountPaise) {
            return Outcome.NOT_PAYABLE;
        }
        Instant now = Instant.now();
        target.units().stream()
                .filter(u -> u.getPaidAt() == null && (coversEverything || Long.valueOf(payerId).equals(u.getAssigneeUserId())))
                .forEach(u -> u.setPaidAt(now));
        booking.setPaidPaise(booking.getPaidPaise() + amountPaise);
        if (booking.getPaidPaise() < booking.getTotalPaise()) {
            return Outcome.STILL_PENDING;
        }
        confirm(target);
        return Outcome.CONFIRMED;
    }

    /** PAYMENT_PENDING -> FAILED (deadline passed, or a seat conflict). Seats are let go after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fail(PayTarget target) {
        Booking booking = target.booking();
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            return;
        }
        booking.transitionTo(BookingStatus.FAILED);
        releaseAfterCommit(target);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> overduePayments() {
        return bookingRepository.findOverduePayments(Instant.now());
    }

    private void confirm(PayTarget target) {
        Booking booking = target.booking();
        List<String> seats = target.units().stream().map(BookingUnit::getUnitRef).toList();
        int sold = inventory(booking).confirm(booking.getItemId(), seats, booking.getId());
        if (sold != seats.size()) {   // should never happen while Redis holds them (architecture.md §5 F8)
            log.error("Seat conflict confirming booking {}: {} of {} seats still available", booking.getBookingCode(), sold, seats.size());
            throw new SeatConflictException("Booking " + booking.getBookingCode() + " lost a seat");
        }
        booking.transitionTo(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(Instant.now());
        booking.setInvoiceNo(invoiceNumbers.next());
        var leader = users.contact(target.group().leaderId());
        booking.setContactEmail(leader.email());
        booking.setContactPhone(leader.phone());
        groups.markBooked(target.group().id());
        releaseAfterCommit(target);   // sold in Postgres now; the Redis holds are no longer needed
    }

    private void releaseAfterCommit(PayTarget target) {
        InventoryProvider inventory = inventory(target.booking());
        long itemId = target.booking().getItemId();
        List<String> seats = target.units().stream().map(BookingUnit::getUnitRef).toList();
        String holder = target.group().publicId().toString();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                inventory.release(itemId, seats, holder);
            }
        });
    }

    private InventoryProvider inventory(Booking booking) {
        return inventories.get(booking.getItemType());
    }
}
