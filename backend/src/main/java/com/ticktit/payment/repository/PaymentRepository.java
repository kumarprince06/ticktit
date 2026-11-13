package com.ticktit.payment.repository;

import com.ticktit.payment.entity.Payment;
import com.ticktit.payment.entity.PaymentCovers;
import com.ticktit.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /** Settlement locks the payment row so verify, webhook and reconciliation can't count it twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> lockById(long id);

    /** An open order for the same thing can be reused instead of making a new one. */
    Optional<Payment> findFirstByBookingIdAndPayerUserIdAndCoversAndAmountPaiseAndStatusOrderByCreatedAtDesc(
            long bookingId, long payerUserId, PaymentCovers covers, long amountPaise, PaymentStatus status);

    List<Payment> findByBookingIdAndStatus(long bookingId, PaymentStatus status);

    List<Payment> findByBookingIdOrderByCreatedAtAsc(long bookingId);

    @Query("SELECT p.id FROM Payment p WHERE p.status = com.ticktit.payment.entity.PaymentStatus.CREATED AND p.createdAt < :before AND p.createdAt > :after")
    List<Long> findOpenBetween(Instant after, Instant before);
}
