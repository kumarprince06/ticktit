package com.ticktit.payment.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Talks to the real Razorpay test-mode API with RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET. Skipped without them. */
class RazorpayGatewayIT {

    private RazorpayGateway razorpay;
    private String secret;

    @BeforeEach
    void setUp() {
        String keyId = System.getenv("RAZORPAY_KEY_ID");
        secret = System.getenv("RAZORPAY_KEY_SECRET");
        assumeTrue(keyId != null && keyId.startsWith("rzp_test_") && secret != null, "Razorpay test keys not set");
        razorpay = new RazorpayGateway(JsonMapper.builder().build(), keyId, secret, "whsec_test", "https://api.razorpay.com/v1");
    }

    @Test
    void createsARealTestOrderAndReadsItBack() {
        PaymentGateway.Order order = razorpay.createOrder(100, "it-" + System.currentTimeMillis(), Map.of("purpose", "integration test"));
        assertTrue(order.id().startsWith("order_"));
        assertEquals(100, order.amountPaise());
        assertEquals("created", order.status());
        assertTrue(razorpay.fetchOrderPayments(order.id()).isEmpty(), "nobody has paid it");
    }

    @Test
    void unknownPaymentIsAGatewayError() {
        GatewayException ex = assertThrows(GatewayException.class, () -> razorpay.fetchPayment("pay_doesNotExist123"));
        assertEquals(400, ex.status());
    }

    @Test
    void checkoutSignatureIsRazorpaysHmac() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String good = HexFormat.of().formatHex(mac.doFinal("order_A|pay_B".getBytes(StandardCharsets.UTF_8)));

        assertTrue(razorpay.verifyCheckoutSignature("order_A", "pay_B", good));
        assertFalse(razorpay.verifyCheckoutSignature("order_A", "pay_C", good), "signature is tied to the payment id");
        assertFalse(razorpay.verifyCheckoutSignature("order_A", "pay_B", null));
    }

    @Test
    void webhookSignatureUsesTheWebhookSecret() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String body = "{\"event\":\"payment.captured\"}";
        String good = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        assertTrue(razorpay.verifyWebhookSignature(body, good));
        assertFalse(razorpay.verifyWebhookSignature(body + " ", good), "any change to the body breaks it");
    }
}
