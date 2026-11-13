package com.ticktit.payment.gateway;

import java.util.List;
import java.util.Map;

/**
 * ADR-011: the only thing payment code knows about a gateway. Razorpay now; another gateway = another adapter.
 * Money state changes only after the server proves it here (signature + fetch, or a verified webhook),
 * never because the browser said so.
 */
public interface PaymentGateway {

    String name();

    /** Public key the browser's checkout needs (Razorpay key_id). Not a secret. */
    String publicKey();

    Order createOrder(long amountPaise, String receipt, Map<String, String> notes);

    Payment fetchPayment(String gatewayPaymentId);

    List<Payment> fetchOrderPayments(String gatewayOrderId);

    /** For gateways where payments are only "authorized" until captured. Returns the captured payment. */
    Payment capture(String gatewayPaymentId, long amountPaise);

    /** Current state of a refund made earlier. */
    Refund fetchRefund(String gatewayRefundId);

    /** {@code reference} makes the call idempotent at the gateway: a retry never refunds twice. */
    Refund refund(String gatewayPaymentId, long amountPaise, String reference);

    /** Checkout's success callback: proves the browser's (order, payment) pair came from the gateway. */
    boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature);

    /** Webhook body signature, checked on the raw bytes before anything is parsed. */
    boolean verifyWebhookSignature(String rawBody, String signature);

    record Order(String id, long amountPaise, String status) {
    }

    /**
     * @param status gateway word: created / authorized / captured / failed / refunded
     * @param methodDetail masked and safe to store, e.g. "VISA ••4242", "HDFC", "ab••@okaxis"
     */
    record Payment(String id, String orderId, long amountPaise, String status, String method, String methodDetail,
                   String errorCode, String errorDescription) {
        public boolean captured() {
            return "captured".equals(status) || "refunded".equals(status);
        }
    }

    /** @param status pending / processed / failed */
    record Refund(String id, String status) {
    }
}
