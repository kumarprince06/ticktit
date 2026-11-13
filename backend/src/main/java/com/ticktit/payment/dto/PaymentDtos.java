package com.ticktit.payment.dto;

import com.ticktit.booking.entity.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Amounts in paise, always computed by the server. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * mode: how the organiser starts the payment (SPLIT or PAY_FOR_ALL); members paying their share send nothing.
     * The organiser can also send PAY_FOR_ALL later to pay whatever is still unpaid.
     */
    public record CheckoutRequest(@NotNull UUID bookingId, PaymentMode mode) {
    }

    /** Everything the browser needs to open the gateway's checkout. keyId is public. */
    public record Checkout(UUID paymentId, String gateway, String keyId, String orderId, long amountPaise, String currency,
                           String bookingCode, String description, Instant paymentDeadline, Prefill prefill) {
    }

    public record Prefill(String name, String email, String contact) {
    }

    /** What the gateway's checkout hands back on success. Proven on the server before anything changes. */
    public record VerifyRequest(@NotBlank @Size(max = 64) String orderId,
                                @NotBlank @Size(max = 64) String paymentId,
                                @NotBlank @Size(max = 128) String signature) {
    }

    public record VerifyResult(String paymentStatus, String bookingStatus) {
    }
}
