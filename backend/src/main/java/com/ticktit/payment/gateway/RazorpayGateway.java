package com.ticktit.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Razorpay over its REST API (https://razorpay.com/docs/api/), Basic auth with key_id:key_secret. */
@Slf4j
@Component
public class RazorpayGateway implements PaymentGateway {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final String baseUrl;

    public RazorpayGateway(JsonMapper json,
                           @Value("${razorpay.key-id:}") String keyId,
                           @Value("${razorpay.key-secret:}") String keySecret,
                           @Value("${razorpay.webhook-secret:}") String webhookSecret,
                           @Value("${razorpay.base-url:https://api.razorpay.com/v1}") String baseUrl) {
        this.json = json;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.baseUrl = baseUrl;
        if (keyId.isBlank() || keySecret.isBlank()) {
            log.warn("Razorpay keys are not set (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET): payments will fail");
        }
    }

    @Override
    public String name() {
        return "RAZORPAY";
    }

    @Override
    public String publicKey() {
        return keyId;
    }

    @Override
    public Order createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode order = call("POST", "/orders", body);
        return new Order(order.get("id").asString(), order.get("amount").asLong(), order.get("status").asString());
    }

    @Override
    public Payment fetchPayment(String gatewayPaymentId) {
        return payment(call("GET", "/payments/" + gatewayPaymentId, null));
    }

    @Override
    public List<Payment> fetchOrderPayments(String gatewayOrderId) {
        List<Payment> payments = new ArrayList<>();
        call("GET", "/orders/" + gatewayOrderId + "/payments", null).get("items").forEach(p -> payments.add(payment(p)));
        return payments;
    }

    @Override
    public Payment capture(String gatewayPaymentId, long amountPaise) {
        return payment(call("POST", "/payments/" + gatewayPaymentId + "/capture", Map.of("amount", amountPaise, "currency", "INR")));
    }

    @Override
    public Refund refund(String gatewayPaymentId, long amountPaise, String reference) {
        JsonNode refund = call("POST", "/payments/" + gatewayPaymentId + "/refund",
                Map.of("amount", amountPaise, "receipt", reference, "speed", "normal"));
        return new Refund(refund.get("id").asString(), refund.get("status").asString());
    }

    @Override
    public Refund fetchRefund(String gatewayRefundId) {
        JsonNode refund = call("GET", "/refunds/" + gatewayRefundId, null);
        return new Refund(refund.get("id").asString(), refund.get("status").asString());
    }

    @Override
    public boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        return matches(hmac(keySecret, gatewayOrderId + "|" + gatewayPaymentId), signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (webhookSecret.isBlank()) {
            log.warn("Webhook received but RAZORPAY_WEBHOOK_SECRET is not set: rejected");
            return false;
        }
        return matches(hmac(webhookSecret, rawBody), signature);
    }

    // ---------------------------------------------------------------- helpers

    private Payment payment(JsonNode p) {
        String method = text(p, "method");
        return new Payment(text(p, "id"), text(p, "order_id"), p.path("amount").asLong(), text(p, "status"), method,
                methodDetail(method, p), text(p, "error_code"), text(p, "error_description"));
    }

    /** Only masked, display-safe details: never a full card number or VPA. */
    private static String methodDetail(String method, JsonNode p) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case "card" -> {
                JsonNode card = p.path("card");
                yield (text(card, "network") == null ? "Card" : text(card, "network")) + " ••" + (text(card, "last4") == null ? "" : text(card, "last4"));
            }
            case "upi" -> maskVpa(text(p, "vpa"));
            case "netbanking" -> text(p, "bank");
            case "wallet" -> text(p, "wallet");
            default -> null;
        };
    }

    private static String maskVpa(String vpa) {
        if (vpa == null || !vpa.contains("@")) {
            return "UPI";
        }
        String user = vpa.substring(0, vpa.indexOf('@'));
        return (user.length() <= 2 ? user : user.substring(0, 2)) + "••" + vpa.substring(vpa.indexOf('@'));
    }

    private JsonNode call(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                String description = json.readTree(response.body()).path("error").path("description").asString(response.body());
                throw new GatewayException("Razorpay " + method + " " + path + " -> " + response.statusCode() + ": " + description,
                        response.statusCode(), null);
            }
            return json.readTree(response.body());
        } catch (GatewayException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("Razorpay call interrupted", 0, e);
        } catch (Exception e) {
            throw new GatewayException("Razorpay unreachable: " + e.getMessage(), 0, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    private static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant time, so response timing reveals nothing about how close a forged signature was. */
    private static boolean matches(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
