package com.ticktit.payment.service;

import com.ticktit.payment.gateway.PaymentGateway;
import com.ticktit.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay webhooks (PAYMENT.md §6): HMAC checked on the raw body before parsing, each event processed once.
 * The payload is treated as a hint only: the payment is fetched from the gateway again before money moves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final PaymentGateway gateway;
    private final PaymentService payments;
    private final RefundService refunds;
    private final PaymentRepository paymentRepository;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    /** @return false when the signature is wrong (400, nothing stored or changed) */
    public boolean handle(String rawBody, String signature, String eventId) {
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            log.warn("SECURITY: webhook with a bad signature rejected (event id {})", eventId);
            return false;
        }
        JsonNode event = json.readTree(rawBody);
        String type = event.path("event").asString("");
        String id = eventId != null && !eventId.isBlank() ? eventId : type + ":" + event.path("created_at").asString("");

        int fresh = jdbc.update("""
                INSERT INTO webhook_events (provider, gateway_event_id, event_type, payload) VALUES (?, ?, ?, ?::jsonb)
                ON CONFLICT (provider, gateway_event_id) DO NOTHING""", gateway.name(), id, type, rawBody);
        Boolean processed = jdbc.queryForObject(
                "SELECT processed_at IS NOT NULL FROM webhook_events WHERE provider = ? AND gateway_event_id = ?",
                Boolean.class, gateway.name(), id);
        if (fresh == 0 && Boolean.TRUE.equals(processed)) {
            return true;   // duplicate delivery: already handled
        }

        try {
            if (type.startsWith("payment.") || type.equals("order.paid")) {
                JsonNode p = event.path("payload").path("payment").path("entity");
                String orderId = p.path("order_id").asString(null);
                String paymentId = p.path("id").asString(null);
                if (orderId != null && paymentId != null) {
                    paymentRepository.findByGatewayOrderId(orderId)
                            .ifPresent(ours -> payments.settle(ours.getId(), gateway.fetchPayment(paymentId)));
                }
            } else if (type.startsWith("refund.")) {
                JsonNode r = event.path("payload").path("refund").path("entity");
                refunds.onGatewayUpdate(r.path("id").asString(), r.path("status").asString());
            }
            jdbc.update("UPDATE webhook_events SET processed_at = now(), error = NULL WHERE provider = ? AND gateway_event_id = ?", gateway.name(), id);
            return true;
        } catch (RuntimeException e) {
            log.error("Webhook {} ({}) failed; the gateway will retry it", id, type, e);
            jdbc.update("UPDATE webhook_events SET error = ? WHERE provider = ? AND gateway_event_id = ?",
                    String.valueOf(e.getMessage()).substring(0, Math.min(255, String.valueOf(e.getMessage()).length())), gateway.name(), id);
            throw e;   // 500: Razorpay retries, and the event isn't marked processed
        }
    }
}
