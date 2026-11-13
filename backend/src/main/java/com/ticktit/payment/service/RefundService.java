package com.ticktit.payment.service;

import com.ticktit.payment.entity.Payment;
import com.ticktit.payment.entity.Refund;
import com.ticktit.payment.entity.RefundReason;
import com.ticktit.payment.entity.RefundStatus;
import com.ticktit.payment.gateway.GatewayException;
import com.ticktit.payment.gateway.PaymentGateway;
import com.ticktit.payment.repository.PaymentRepository;
import com.ticktit.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * REFUND.md: a refund row is written in the same transaction as the reason for it, then a job sends it to the
 * gateway. The gateway call carries "refund-{id}" as its reference, and on any doubt we ask the gateway what
 * already happened before trying again, so a retry never refunds twice. 3 tries (1, 5, 15 min), then FAILED + alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;

    /** At most one refund per payment; asking twice is harmless. Runs in the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void request(Payment payment, RefundReason reason) {
        if (refundRepository.findByPaymentId(payment.getId()).isPresent()) {
            return;
        }
        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setBookingId(payment.getBookingId());
        refund.setAmountPaise(payment.getAmountPaise());
        refund.setReason(reason);
        refundRepository.save(refund);
        log.info("Refund requested: {} paise for payment {} ({})", payment.getAmountPaise(), payment.getPublicId(), reason);
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 20_000)
    public void processDue() {
        for (Long id : tx.execute(s -> refundRepository.findDue(Instant.now()))) {
            try {
                process(id);
            } catch (RuntimeException e) {
                log.error("Refund {} processing crashed", id, e);
            }
        }
    }

    /** Sends one refund to the gateway, or checks on one already sent. Public for tests. */
    public void process(long refundId) {
        Refund refund = tx.execute(s -> refundRepository.findById(refundId).orElseThrow());
        String gatewayPaymentId = paymentGatewayId(refund);
        PaymentGateway.Refund result;
        try {
            result = refund.getGatewayRefundId() != null
                    ? gateway.fetchRefund(refund.getGatewayRefundId())
                    : gateway.refund(gatewayPaymentId, refund.getAmountPaise(), "refund-" + refund.getPublicId());
        } catch (GatewayException e) {
            // Maybe it went through and only the answer was lost: ask before trying again
            boolean alreadyRefunded = false;
            try {
                alreadyRefunded = "refunded".equals(gateway.fetchPayment(gatewayPaymentId).status());
            } catch (GatewayException ignored) {
                // gateway down: just retry later
            }
            if (alreadyRefunded) {
                tx.executeWithoutResult(s -> updateStatus(refundId, null, RefundStatus.SUCCESS, null));
            } else {
                tx.executeWithoutResult(s -> failedAttempt(refundId, e.getMessage()));
            }
            return;
        }
        RefundStatus status = switch (result.status()) {
            case "processed" -> RefundStatus.SUCCESS;
            case "failed" -> RefundStatus.FAILED;
            default -> RefundStatus.PROCESSING;   // pending: check again later (or a webhook will tell us)
        };
        tx.executeWithoutResult(s -> updateStatus(refundId, result.id(), status, null));
    }

    /** From a verified refund.processed / refund.failed webhook. */
    @Transactional
    public void onGatewayUpdate(String gatewayRefundId, String gatewayStatus) {
        refundRepository.findByGatewayRefundId(gatewayRefundId).ifPresent(r ->
                updateStatus(r.getId(), gatewayRefundId, "processed".equals(gatewayStatus) ? RefundStatus.SUCCESS : RefundStatus.FAILED, null));
    }

    private void updateStatus(long refundId, String gatewayRefundId, RefundStatus status, String error) {
        Refund r = refundRepository.findById(refundId).orElseThrow();
        if (gatewayRefundId != null) {
            r.setGatewayRefundId(gatewayRefundId);
        }
        r.setStatus(status);
        r.setLastError(error);
        r.setAttempts(r.getAttempts() + 1);
        if (status == RefundStatus.PROCESSING) {
            r.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(2)));
        }
        if (status == RefundStatus.FAILED) {
            log.error("Refund {} FAILED at the gateway: needs an admin", r.getPublicId());
        }
    }

    private void failedAttempt(long refundId, String error) {
        Refund r = refundRepository.findById(refundId).orElseThrow();
        r.setAttempts(r.getAttempts() + 1);
        r.setLastError(error == null ? null : error.substring(0, Math.min(255, error.length())));
        if (r.getAttempts() > BACKOFF.size()) {
            r.setStatus(RefundStatus.FAILED);
            log.error("Refund {} FAILED after {} tries: needs an admin. Last error: {}", r.getPublicId(), r.getAttempts(), error);
        } else {
            r.setNextAttemptAt(Instant.now().plus(BACKOFF.get(r.getAttempts() - 1)));
            log.warn("Refund {} try {} failed, retrying later: {}", r.getPublicId(), r.getAttempts(), error);
        }
    }

    private String paymentGatewayId(Refund refund) {
        return tx.execute(st -> paymentRepository.findById(refund.getPaymentId()).orElseThrow().getGatewayPaymentId());
    }
}
