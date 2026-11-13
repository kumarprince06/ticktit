package com.ticktit.payment.service;

import com.ticktit.booking.service.BookingLifecycle;
import com.ticktit.payment.entity.Payment;
import com.ticktit.payment.entity.PaymentStatus;
import com.ticktit.payment.gateway.GatewayException;
import com.ticktit.payment.gateway.PaymentGateway;
import com.ticktit.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Time-driven money work, from Postgres deadlines only (ADR-013), never from Redis expiry.
 * ponytail: every app instance runs these; all steps are row-locked and idempotent, so duplicates are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentJobs {

    private final PaymentService payments;
    private final PaymentRepository paymentRepository;
    private final BookingLifecycle lifecycle;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;

    /** Payment window over with money missing: booking FAILED, everyone who paid is refunded (PAYMENT.md step 9). */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void expireOverdue() {
        List<Long> overdue = tx.execute(s -> lifecycle.overduePayments());
        for (Long bookingId : overdue) {
            try {
                payments.expire(bookingId);
            } catch (RuntimeException e) {
                log.error("Couldn't expire booking {}", bookingId, e);
            }
        }
    }

    /**
     * PAYMENT.md F3: the browser closed before verify, or no webhook came. Ask the gateway about orders that have
     * been open for 2+ minutes (up to a day) and settle whatever it reports.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void reconcile() {
        Instant now = Instant.now();
        List<Long> open = tx.execute(s -> paymentRepository.findOpenBetween(now.minus(Duration.ofDays(1)), now.minus(Duration.ofMinutes(2))));
        for (Long id : open) {
            Payment payment = tx.execute(s -> paymentRepository.findById(id).orElseThrow());
            try {
                for (PaymentGateway.Payment p : gateway.fetchOrderPayments(payment.getGatewayOrderId())) {
                    payments.settle(id, p);
                }
            } catch (GatewayException e) {
                log.warn("Reconciliation couldn't reach the gateway for {}: {}", payment.getGatewayOrderId(), e.getMessage());
                return;   // gateway down: try again next round
            }
            // still unpaid and the booking has moved on: close the order
            tx.executeWithoutResult(s -> paymentRepository.findById(id)
                    .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                    .filter(p -> !lifecycle.lockForSettlement(p.getBookingId()).booking().getStatus().isActive())
                    .ifPresent(p -> p.setStatus(PaymentStatus.CANCELLED)));
        }
    }
}
