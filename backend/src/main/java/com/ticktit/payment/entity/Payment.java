package com.ticktit.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One pay attempt = one gateway order. Card numbers, CVV and UPI PINs are never stored. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private Long bookingId;

    @Column(nullable = false, updatable = false)
    private Long payerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentCovers covers;

    @Column(nullable = false, updatable = false)
    private long amountPaise;

    @Column(nullable = false, updatable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String gateway;

    @Column(nullable = false, updatable = false)
    private String gatewayOrderId;

    private String gatewayPaymentId;
    private String method;
    private String methodDetail;
    private String failureCode;
    private String failureReason;
    private Instant succeededAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
