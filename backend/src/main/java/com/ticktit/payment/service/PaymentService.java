package com.ticktit.payment.service;

import com.ticktit.auth.service.UserDirectory;
import com.ticktit.auth.service.UserDirectory.UserCard;
import com.ticktit.booking.entity.Booking;
import com.ticktit.booking.entity.BookingStatus;
import com.ticktit.booking.entity.PaymentMode;
import com.ticktit.booking.service.BookingLifecycle;
import com.ticktit.booking.service.BookingLifecycle.Outcome;
import com.ticktit.booking.service.BookingLifecycle.PayTarget;
import com.ticktit.booking.service.BookingLifecycle.SeatConflictException;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.common.idempotency.IdempotencyService;
import com.ticktit.payment.dto.PaymentDtos.Checkout;
import com.ticktit.payment.dto.PaymentDtos.CheckoutRequest;
import com.ticktit.payment.dto.PaymentDtos.Prefill;
import com.ticktit.payment.dto.PaymentDtos.VerifyRequest;
import com.ticktit.payment.dto.PaymentDtos.VerifyResult;
import com.ticktit.payment.entity.Payment;
import com.ticktit.payment.entity.PaymentAttempt;
import com.ticktit.payment.entity.PaymentCovers;
import com.ticktit.payment.entity.PaymentStatus;
import com.ticktit.payment.entity.RefundReason;
import com.ticktit.payment.gateway.GatewayException;
import com.ticktit.payment.gateway.PaymentGateway;
import com.ticktit.payment.repository.PaymentAttemptRepository;
import com.ticktit.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Payments (PAYMENT.md). Who pays: Split Pay (each member their seats) or Pay For All (the organiser pays everything
 * still unpaid; a solo booking is always this). The server computes every amount. A payment counts only after the
 * server proves it with the gateway: signature + fetch (verify), a signed webhook, or the reconciliation job, which
 * all end in {@link #settle}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final BookingLifecycle lifecycle;
    private final RefundService refunds;
    private final PaymentGateway gateway;
    private final UserDirectory users;
    private final IdempotencyService idempotency;
    private final TransactionTemplate tx;

    // ---------------------------------------------------------------- checkout

    /**
     * Starts the payment window if needed (organiser) and returns gateway checkout data for what the caller owes.
     * The same open order is reused while it's unpaid, so re-opening checkout never creates duplicates.
     */
    @Transactional
    public Checkout checkout(UUID callerId, String idempotencyKey, CheckoutRequest request) {
        UserCard caller = users.requireActive(callerId);
        return idempotency.run(caller.id(), idempotencyKey, "POST /payments/checkout", request, Checkout.class,
                () -> doCheckout(caller, idempotencyKey, request));
    }

    private Checkout doCheckout(UserCard caller, String idempotencyKey, CheckoutRequest request) {
        PayTarget target = lifecycle.loadForPayment(request.bookingId(), caller.id());
        Booking booking = target.booking();
        boolean leader = target.group().isLeader(caller.id());
        boolean alone = target.group().memberIds().size() == 1;
        PaymentMode wanted = request.mode() != null ? request.mode() : (alone ? PaymentMode.PAY_FOR_ALL : PaymentMode.SPLIT);

        switch (booking.getStatus()) {
            case LOCKED -> {
                if (!leader) {
                    throw new ApiException(ErrorCode.NOT_LEADER);
                }
                lifecycle.startPaymentWindow(target, alone ? PaymentMode.PAY_FOR_ALL : wanted);
            }
            case PAYMENT_PENDING -> {
                if (!booking.getPaymentDeadline().isAfter(Instant.now())) {
                    throw new ApiException(ErrorCode.PAYMENT_WINDOW_CLOSED);
                }
            }
            case CONFIRMED -> throw new ApiException(ErrorCode.ALREADY_PAID);
            default -> throw new ApiException(ErrorCode.BOOKING_NOT_LOCKED);
        }

        // Pay For All: the organiser pays everything left (also mid-window, e.g. to cover a slow friend)
        boolean everything = booking.getMode() == PaymentMode.PAY_FOR_ALL || wanted == PaymentMode.PAY_FOR_ALL;
        if (everything && !leader) {
            throw new ApiException(ErrorCode.NOT_LEADER);
        }
        PaymentCovers covers = everything ? PaymentCovers.EVERYTHING_LEFT : PaymentCovers.OWN_SHARE;
        long amount = everything ? target.everythingLeft() : target.ownShareLeft(caller.id());
        if (amount == 0) {
            throw new ApiException(ErrorCode.ALREADY_PAID);
        }

        Payment payment = paymentRepository
                .findFirstByBookingIdAndPayerUserIdAndCoversAndAmountPaiseAndStatusOrderByCreatedAtDesc(
                        booking.getId(), caller.id(), covers, amount, PaymentStatus.CREATED)
                .orElse(null);
        if (payment == null) {
            PaymentGateway.Order order;
            try {
                order = gateway.createOrder(amount, booking.getBookingCode() + "-" + Long.toString(System.nanoTime(), 36),
                        Map.of("booking", booking.getBookingCode(), "payer", caller.publicId().toString(), "covers", covers.name()));
            } catch (GatewayException e) {
                log.error("Couldn't create gateway order for booking {}: {}", booking.getBookingCode(), e.getMessage());
                throw new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
            }
            payment = new Payment();
            payment.setBookingId(booking.getId());
            payment.setPayerUserId(caller.id());
            payment.setCovers(covers);
            payment.setAmountPaise(amount);
            payment.setIdempotencyKey(idempotencyKey);
            payment.setGateway(gateway.name());
            payment.setGatewayOrderId(order.id());
            paymentRepository.save(payment);
        }

        var contact = users.contact(caller.id());
        String seats = target.units().size() + " seat" + (target.units().size() == 1 ? "" : "s");
        return new Checkout(payment.getPublicId(), gateway.name(), gateway.publicKey(), payment.getGatewayOrderId(),
                payment.getAmountPaise(), payment.getCurrency(), booking.getBookingCode(),
                (everything ? "All seats" : "Your share") + " · booking " + booking.getBookingCode() + " · " + seats,
                booking.getPaymentDeadline(), new Prefill(contact.name(), contact.email(), contact.phone()));
    }

    // ---------------------------------------------------------------- verify (checkout success callback)

    /**
     * The browser says "paid". We check the signature (proves the ids came from the gateway), then ask the gateway
     * itself about the payment before any money is counted.
     */
    public VerifyResult verify(UUID callerId, VerifyRequest request) {
        UserCard caller = users.requireActive(callerId);
        Payment payment = paymentRepository.findByGatewayOrderId(request.orderId())
                .filter(p -> p.getPayerUserId() == caller.id())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!gateway.verifyCheckoutSignature(request.orderId(), request.paymentId(), request.signature())) {
            log.warn("Bad checkout signature for order {} (payment {})", request.orderId(), request.paymentId());
            throw new ApiException(ErrorCode.PAYMENT_SIGNATURE_INVALID);   // reconciliation will still find real money
        }
        PaymentGateway.Payment fromGateway;
        try {
            fromGateway = gateway.fetchPayment(request.paymentId());
        } catch (GatewayException e) {
            throw new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);   // reconciliation finishes it later
        }
        settle(payment.getId(), fromGateway);
        return tx.execute(s -> {
            Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
            return new VerifyResult(p.getStatus().name(), lifecycle.lockForSettlement(p.getBookingId()).booking().getStatus().name());
        });
    }

    // ---------------------------------------------------------------- settle: the one place money is counted

    /**
     * Applies what the gateway says about one of our payments. Safe to call any number of times, from verify, a
     * webhook or reconciliation: the payment row is locked and a SUCCESS is only ever counted once.
     */
    public void settle(long paymentId, PaymentGateway.Payment fromGateway) {
        Payment snapshot = tx.execute(s -> paymentRepository.findById(paymentId).orElseThrow());
        if (!snapshot.getGatewayOrderId().equals(fromGateway.orderId())) {
            log.warn("Gateway payment {} belongs to order {}, not {}: ignored", fromGateway.id(), fromGateway.orderId(), snapshot.getGatewayOrderId());
            return;
        }
        PaymentGateway.Payment state = fromGateway;
        if ("authorized".equals(state.status())) {   // account without auto-capture: capture it ourselves
            try {
                state = gateway.capture(state.id(), state.amountPaise());
            } catch (GatewayException e) {
                log.warn("Capture of {} failed, reconciliation will retry: {}", state.id(), e.getMessage());
                return;
            }
        }
        PaymentGateway.Payment captured = state;
        try {
            tx.executeWithoutResult(s -> apply(paymentId, captured));
        } catch (SeatConflictException e) {
            // the confirm rolled back; record the money and give everything back (PAYMENT.md F8)
            tx.executeWithoutResult(s -> applySeatConflict(paymentId, captured));
        }
    }

    private void apply(long paymentId, PaymentGateway.Payment g) {
        Payment payment = paymentRepository.lockById(paymentId).orElseThrow();
        recordAttempt(payment, g);
        if ("failed".equals(g.status())) {
            if (payment.getStatus() == PaymentStatus.CREATED) {   // order stays open: the user can try again
                payment.setFailureCode(g.errorCode());
                payment.setFailureReason(truncate(g.errorDescription()));
            }
            return;
        }
        if (!g.captured() || payment.getStatus() == PaymentStatus.SUCCESS) {
            return;   // not money yet, or already counted
        }
        markSucceeded(payment, g);
        if (g.amountPaise() != payment.getAmountPaise()) {   // PAYMENT.md F7: never trust a different amount
            log.error("Amount mismatch on {}: expected {} got {}", payment.getPublicId(), payment.getAmountPaise(), g.amountPaise());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Amount mismatch: refunded for review");
            refunds.request(payment, RefundReason.AMOUNT_MISMATCH);
            return;
        }
        PayTarget target = lifecycle.lockForSettlement(payment.getBookingId());
        boolean wasPending = target.booking().getStatus() == BookingStatus.PAYMENT_PENDING;
        Outcome outcome = lifecycle.recordPayment(target, payment.getPayerUserId(),
                payment.getCovers() == PaymentCovers.EVERYTHING_LEFT, payment.getAmountPaise());
        switch (outcome) {
            case NOT_PAYABLE -> refunds.request(payment, wasPending ? RefundReason.OVERPAID : RefundReason.LATE_PAYMENT);
            case CONFIRMED -> {
                log.info("Booking {} CONFIRMED", target.booking().getBookingCode());
                cancelOpenOrders(target.bookingId());
            }
            case STILL_PENDING -> { }
        }
    }

    private void applySeatConflict(long paymentId, PaymentGateway.Payment g) {
        Payment payment = paymentRepository.lockById(paymentId).orElseThrow();
        recordAttempt(payment, g);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            markSucceeded(payment, g);
        }
        PayTarget target = lifecycle.lockForSettlement(payment.getBookingId());
        lifecycle.fail(target);
        refundAllPaid(target.bookingId(), RefundReason.SEAT_CONFLICT);
        cancelOpenOrders(target.bookingId());
    }

    /** Deadline passed (PaymentDeadlineJob): fail the booking, refund everyone who paid, close open orders. */
    @Transactional
    public void expire(long bookingId) {
        PayTarget target = lifecycle.lockForSettlement(bookingId);
        if (target.booking().getStatus() != BookingStatus.PAYMENT_PENDING
                || target.booking().getPaymentDeadline().isAfter(Instant.now())) {
            return;
        }
        lifecycle.fail(target);
        refundAllPaid(bookingId, RefundReason.TIMER_EXPIRED);
        cancelOpenOrders(bookingId);
        log.info("Booking {} FAILED: payment time ran out", target.booking().getBookingCode());
    }

    private void refundAllPaid(long bookingId, RefundReason reason) {
        paymentRepository.findByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESS).forEach(p -> refunds.request(p, reason));
    }

    private void cancelOpenOrders(long bookingId) {
        paymentRepository.findByBookingIdAndStatus(bookingId, PaymentStatus.CREATED).forEach(p -> p.setStatus(PaymentStatus.CANCELLED));
    }

    private void markSucceeded(Payment payment, PaymentGateway.Payment g) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(g.id());
        payment.setMethod(g.method());
        payment.setMethodDetail(g.methodDetail());
        payment.setFailureCode(null);
        payment.setFailureReason(null);
        payment.setSucceededAt(Instant.now());
    }

    private void recordAttempt(Payment payment, PaymentGateway.Payment g) {
        PaymentAttempt attempt = attemptRepository.findByGatewayPaymentId(g.id()).orElseGet(PaymentAttempt::new);
        attempt.setPaymentId(payment.getId());
        attempt.setGatewayPaymentId(g.id());
        attempt.setStatus(g.status());
        attempt.setAmountPaise(g.amountPaise());
        attempt.setMethod(g.method());
        attempt.setMethodDetail(g.methodDetail());
        attempt.setErrorCode(g.errorCode());
        attempt.setErrorDescription(truncate(g.errorDescription()));
        attemptRepository.save(attempt);
    }

    private static String truncate(String s) {
        return s == null ? null : s.substring(0, Math.min(255, s.length()));
    }
}
