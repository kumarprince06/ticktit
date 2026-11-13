package com.ticktit.payment.controller;

import com.ticktit.payment.dto.PaymentDtos.Checkout;
import com.ticktit.payment.dto.PaymentDtos.CheckoutRequest;
import com.ticktit.payment.dto.PaymentDtos.VerifyRequest;
import com.ticktit.payment.dto.PaymentDtos.VerifyResult;
import com.ticktit.payment.service.PaymentService;
import com.ticktit.payment.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService payments;
    private final WebhookService webhooks;

    /** Idempotency-Key required (ADR-010): one per Pay tap, reused on retries. */
    @PostMapping("/payments/checkout")
    public Checkout checkout(@AuthenticationPrincipal Jwt jwt, @RequestHeader(name = "Idempotency-Key", required = false) String key,
                             @Valid @RequestBody CheckoutRequest request) {
        return payments.checkout(UUID.fromString(jwt.getSubject()), key, request);
    }

    @PostMapping("/payments/verify")
    public VerifyResult verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VerifyRequest request) {
        return payments.verify(UUID.fromString(jwt.getSubject()), request);
    }

    /** Public, but every request must carry Razorpay's HMAC signature of the raw body. */
    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Void> razorpayWebhook(@RequestBody String rawBody,
                                                @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
                                                @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        return webhooks.handle(rawBody, signature, eventId) ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }
}
