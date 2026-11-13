package com.ticktit.payment.repository;

import com.ticktit.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentId(long paymentId);

    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);

    List<Refund> findByBookingId(long bookingId);

    @Query("""
            SELECT r.id FROM Refund r WHERE r.status IN (com.ticktit.payment.entity.RefundStatus.INITIATED,
                com.ticktit.payment.entity.RefundStatus.PROCESSING) AND r.nextAttemptAt <= :now""")
    List<Long> findDue(Instant now);
}
