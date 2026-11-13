package com.ticktit.payment.repository;

import com.ticktit.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByGatewayPaymentId(String gatewayPaymentId);
}
