package com.ticktit.payment.entity;

/** CREATED = order made, waiting for money. Changes only on proof from the gateway (ADR-011). */
public enum PaymentStatus {
    CREATED,
    SUCCESS,
    FAILED,
    CANCELLED
}
