package com.ticktit.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Every try at the gateway on an order, failed ones included. */
@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long paymentId;

    @Column(nullable = false, updatable = false)
    private String gatewayPaymentId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private long amountPaise;

    private String method;
    private String methodDetail;
    private String errorCode;
    private String errorDescription;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
