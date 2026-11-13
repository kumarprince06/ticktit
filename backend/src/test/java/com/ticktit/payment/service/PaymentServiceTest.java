package com.ticktit.payment.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.booking.dto.BookingDtos.AssignRequest;
import com.ticktit.booking.dto.BookingDtos.Assignment;
import com.ticktit.booking.dto.BookingDtos.BookingView;
import com.ticktit.booking.dto.BookingDtos.LockRequest;
import com.ticktit.booking.entity.PaymentMode;
import com.ticktit.booking.service.BookingService;
import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import com.ticktit.group.service.GroupService;
import com.ticktit.movie.service.CatalogService;
import com.ticktit.payment.dto.PaymentDtos.Checkout;
import com.ticktit.payment.dto.PaymentDtos.CheckoutRequest;
import com.ticktit.payment.dto.PaymentDtos.VerifyRequest;
import com.ticktit.payment.gateway.GatewayException;
import com.ticktit.payment.gateway.PaymentGateway;
import com.ticktit.payment.repository.RefundRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Every payment scenario from PAYMENT.md / REFUND.md, against real Postgres + Redis. The gateway is a Mockito
 * stand-in so each case (success, failure, bad signature, timeout...) can be forced; RazorpayGatewayIT covers the
 * real Razorpay API.
 */
@SpringBootTest
class PaymentServiceTest {

    @Autowired PaymentService payments;
    @Autowired PaymentJobs jobs;
    @Autowired RefundService refunds;
    @Autowired WebhookService webhooks;
    @Autowired BookingService bookings;
    @Autowired GroupService groups;
    @Autowired CatalogService catalog;
    @Autowired UserRepository userRepository;
    @Autowired RefundRepository refundRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoBean PaymentGateway gateway;

    private final AtomicInteger seq = new AtomicInteger();
    long showId;
    UUID asha, rahul;

    @BeforeEach
    void setUp() {
        showId = catalog.shows("the-ninth-over", "mumbai", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(3)).getFirst().id();
        clearLocks();
        asha = user("asha");
        rahul = user("rahul");
        stubGateway();
    }

    private void stubGateway() {
        when(gateway.name()).thenReturn("RAZORPAY");
        when(gateway.publicKey()).thenReturn("rzp_test_fake");
        when(gateway.createOrder(anyLong(), anyString(), anyMap()))
                .thenAnswer(i -> new PaymentGateway.Order("order_" + seq.incrementAndGet(), i.getArgument(0), "created"));
        when(gateway.verifyCheckoutSignature(anyString(), anyString(), eq("good-signature"))).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        clearLocks();
        String testUsers = "SELECT id FROM users WHERE email LIKE 'pay-test-%'";
        String testBookings = "SELECT b.id FROM bookings b JOIN groups g ON g.id = b.group_id WHERE g.leader_id IN (" + testUsers + ")";
        jdbc.update("DELETE FROM refunds WHERE booking_id IN (" + testBookings + ")");
        jdbc.update("DELETE FROM webhook_events WHERE gateway_event_id LIKE 'evt_%'");
        jdbc.update("DELETE FROM payments WHERE booking_id IN (" + testBookings + ")");
        jdbc.update("UPDATE show_seats SET status = 'AVAILABLE', booking_id = NULL WHERE booking_id IN (" + testBookings + ") OR (show_id = ? AND status = 'BLOCKED')", showId);
        jdbc.update("DELETE FROM bookings WHERE id IN (" + testBookings + ")");
        jdbc.update("DELETE FROM groups WHERE leader_id IN (" + testUsers + ")");
        jdbc.update("DELETE FROM users WHERE email LIKE 'pay-test-%'");
    }

    // ---------------------------------------------------------------- solo / pay for all

    @Test
    void soloBookingPaysEverythingAndIsConfirmed() {
        UUID group = groups.create(asha, showId, true).groupId();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("H1", "H2")));

        Checkout checkout = payments.checkout(asha, key(), new CheckoutRequest(locked.id(), null));
        assertEquals(locked.totalPaise(), checkout.amountPaise());
        assertEquals("rzp_test_fake", checkout.keyId());
        assertEquals(checkout.orderId(), payments.checkout(asha, key(), new CheckoutRequest(locked.id(), null)).orderId(),
                "reopening checkout reuses the open order");

        pay(asha, checkout, "captured");

        BookingView done = bookings.current(asha, group).orElseThrow();
        assertEquals("CONFIRMED", done.status());
        assertTrue(done.invoiceNo().matches("TT/\\d{4}-\\d{2}/\\d{6}"), done.invoiceNo());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM show_seats WHERE show_id = ? AND seat_no IN ('H1','H2') AND status = 'BOOKED'", Integer.class, showId));
        assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM groups WHERE public_id = ?", String.class, group));
        assertNull(redis.opsForValue().get("lock:show:" + showId + ":seat:H1"), "Redis hold released once sold");
    }

    // ---------------------------------------------------------------- split pay

    @Test
    void splitPayConfirmsOnlyWhenEveryoneHasPaid() {
        UUID group = twoPersonGroup();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("J4", "J5")));

        Checkout ashaShare = payments.checkout(asha, key(), new CheckoutRequest(locked.id(), PaymentMode.SPLIT));
        Checkout rahulShare = payments.checkout(rahul, key(), new CheckoutRequest(locked.id(), null));
        assertEquals(locked.totalPaise() / 2, ashaShare.amountPaise());
        assertEquals(locked.totalPaise() / 2, rahulShare.amountPaise());

        pay(asha, ashaShare, "captured");
        BookingView half = bookings.current(rahul, group).orElseThrow();
        assertEquals("PAYMENT_PENDING", half.status());
        assertEquals(rahulShare.amountPaise(), half.youOwePaise());
        assertTrue(half.shares().getFirst().paid());
        assertFalse(half.shares().get(1).paid());

        pay(rahul, rahulShare, "captured");
        assertEquals("CONFIRMED", bookings.current(rahul, group).orElseThrow().status());
    }

    @Test
    void splitNeedsEverySeatAssignedAndEveryoneSeated() {
        UUID group = twoPersonGroup();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("J6", "J7")));
        bookings.assign(asha, key(), locked.id(), new AssignRequest(List.of(new Assignment("J7", asha))));   // Rahul has none

        ApiException ex = assertThrows(ApiException.class,
                () -> payments.checkout(asha, key(), new CheckoutRequest(locked.id(), PaymentMode.SPLIT)));
        assertEquals(ErrorCode.SEATS_NOT_ASSIGNED, ex.getErrorCode());
    }

    @Test
    void organiserCanPayForEveryoneMidWindow() {
        UUID group = twoPersonGroup();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("K4", "K5")));
        Checkout ashaShare = payments.checkout(asha, key(), new CheckoutRequest(locked.id(), PaymentMode.SPLIT));
        pay(asha, ashaShare, "captured");

        Checkout rest = payments.checkout(asha, key(), new CheckoutRequest(locked.id(), PaymentMode.PAY_FOR_ALL));
        assertEquals(locked.totalPaise() - ashaShare.amountPaise(), rest.amountPaise());
        pay(asha, rest, "captured");

        assertEquals("CONFIRMED", bookings.current(rahul, group).orElseThrow().status());
        assertEquals(ErrorCode.ALREADY_PAID, assertThrows(ApiException.class,
                () -> payments.checkout(rahul, key(), new CheckoutRequest(locked.id(), null))).getErrorCode());
    }

    @Test
    void onlyTheOrganiserStartsPayment() {
        UUID group = twoPersonGroup();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("K6", "K7")));
        assertEquals(ErrorCode.NOT_LEADER, assertThrows(ApiException.class,
                () -> payments.checkout(rahul, key(), new CheckoutRequest(locked.id(), null))).getErrorCode());
    }

    // ---------------------------------------------------------------- proof and safety

    @Test
    void forgedSignatureChangesNothing() {
        Checkout checkout = soloCheckout("H5");
        ApiException ex = assertThrows(ApiException.class,
                () -> payments.verify(asha, new VerifyRequest(checkout.orderId(), "pay_x", "forged")));
        assertEquals(ErrorCode.PAYMENT_SIGNATURE_INVALID, ex.getErrorCode());
        verify(gateway, never()).fetchPayment(anyString());
        assertEquals("CREATED", paymentStatus(checkout));
    }

    @Test
    void failedAttemptKeepsTheOrderOpenAndARetrySucceeds() {
        Checkout checkout = soloCheckout("H6");
        pay(asha, checkout, "failed");
        assertEquals("CREATED", paymentStatus(checkout));
        assertEquals("BAD_REQUEST_ERROR", jdbc.queryForObject("SELECT failure_code FROM payments WHERE gateway_order_id = ?", String.class, checkout.orderId()));

        pay(asha, checkout, "captured");   // second try on the same order
        assertEquals("SUCCESS", paymentStatus(checkout));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM payment_attempts a JOIN payments p ON p.id = a.payment_id WHERE p.gateway_order_id = ?", Integer.class, checkout.orderId()));
    }

    @Test
    void sameMoneyReportedTwiceIsCountedOnce() {
        Checkout checkout = soloCheckout("H7");
        PaymentGateway.Payment captured = gatewayPayment(checkout, "pay_dup", "captured", checkout.amountPaise());
        when(gateway.fetchPayment("pay_dup")).thenReturn(captured);
        payments.verify(asha, new VerifyRequest(checkout.orderId(), "pay_dup", "good-signature"));
        long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE gateway_order_id = ?", Long.class, checkout.orderId());
        payments.settle(paymentId, captured);   // webhook says it again
        payments.settle(paymentId, captured);   // reconciliation says it again

        assertEquals(checkout.amountPaise(), jdbc.queryForObject("SELECT paid_paise FROM bookings b JOIN payments p ON p.booking_id = b.id WHERE p.id = ?", Long.class, paymentId));
        assertEquals(0, refundRepository.count() - baselineRefunds(), "nothing to refund");
    }

    @Test
    void differentAmountIsRefundedForReview() {
        Checkout checkout = soloCheckout("H8");
        pay(asha, checkout, "captured", checkout.amountPaise() - 100);
        assertEquals("FAILED", paymentStatus(checkout));
        assertEquals("AMOUNT_MISMATCH", refundReason(checkout));
    }

    @Test
    void gatewayDownOnCheckoutLeavesNothingHalfDone() {
        UUID group = groups.create(asha, showId, true).groupId();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("H9")));
        when(gateway.createOrder(anyLong(), anyString(), anyMap())).thenThrow(new GatewayException("down", 0, null));

        String key = key();
        assertEquals(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, assertThrows(ApiException.class,
                () -> payments.checkout(asha, key, new CheckoutRequest(locked.id(), null))).getErrorCode());
        assertEquals("LOCKED", bookings.current(asha, group).orElseThrow().status(), "window not started");

        reset(gateway);
        stubGateway();   // gateway back
        assertNotNull(payments.checkout(asha, key, new CheckoutRequest(locked.id(), null)).orderId(), "same key works after the outage");
    }

    // ---------------------------------------------------------------- deadline, late money, refunds

    @Test
    void deadlinePassingFailsTheBookingAndRefundsWhoPaid() {
        UUID group = twoPersonGroup();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("G1", "G2")));
        Checkout ashaShare = payments.checkout(asha, key(), new CheckoutRequest(locked.id(), PaymentMode.SPLIT));
        Checkout rahulShare = payments.checkout(rahul, key(), new CheckoutRequest(locked.id(), null));
        pay(asha, ashaShare, "captured");

        jdbc.update("UPDATE bookings SET payment_deadline = now() - interval '1 second' WHERE public_id = ?", locked.id());
        jobs.expireOverdue();

        assertEquals("FAILED", bookings.current(asha, group).orElseThrow().status());
        assertEquals("TIMER_EXPIRED", refundReason(ashaShare));
        assertEquals("CANCELLED", paymentStatus(rahulShare), "Rahul's open order is closed");
        assertNull(redis.opsForValue().get("lock:show:" + showId + ":seat:G1"), "seats freed");

        // REFUND.md: the gateway call carries refund-{id} so a retry never refunds twice
        when(gateway.refund(anyString(), anyLong(), anyString())).thenReturn(new PaymentGateway.Refund("rfnd_1", "processed"));
        long refundId = jdbc.queryForObject("SELECT r.id FROM refunds r JOIN payments p ON p.id = r.payment_id WHERE p.gateway_order_id = ?", Long.class, ashaShare.orderId());
        refunds.process(refundId);
        verify(gateway).refund(eq("pay_" + ashaShare.orderId()), eq(ashaShare.amountPaise()), startsWith("refund-"));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, refundId));
    }

    @Test
    void moneyArrivingAfterTheDeadlineIsRefunded() {
        UUID group = twoPersonGroup();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of("G3", "G4")));
        payments.checkout(asha, key(), new CheckoutRequest(locked.id(), PaymentMode.SPLIT));
        Checkout rahulShare = payments.checkout(rahul, key(), new CheckoutRequest(locked.id(), null));
        jdbc.update("UPDATE bookings SET payment_deadline = now() - interval '1 second' WHERE public_id = ?", locked.id());
        jobs.expireOverdue();

        long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE gateway_order_id = ?", Long.class, rahulShare.orderId());
        payments.settle(paymentId, gatewayPayment(rahulShare, "pay_late", "captured", rahulShare.amountPaise()));   // e.g. a late webhook

        assertEquals("SUCCESS", paymentStatus(rahulShare));
        assertEquals("LATE_PAYMENT", refundReason(rahulShare));
    }

    @Test
    void refundRetriesThenGivesUpLoudly() {
        Checkout checkout = soloCheckout("G5");
        pay(asha, checkout, "captured", checkout.amountPaise() - 1);   // mismatch -> refund requested
        long refundId = jdbc.queryForObject("SELECT r.id FROM refunds r JOIN payments p ON p.id = r.payment_id WHERE p.gateway_order_id = ?", Long.class, checkout.orderId());
        when(gateway.refund(anyString(), anyLong(), anyString())).thenThrow(new GatewayException("timeout", 0, null));
        when(gateway.fetchPayment(anyString())).thenReturn(gatewayPayment(checkout, "pay_x", "captured", 1));

        for (int i = 0; i < 4; i++) {
            refunds.process(refundId);
        }
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, refundId));
        assertEquals(4, jdbc.queryForObject("SELECT attempts FROM refunds WHERE id = ?", Integer.class, refundId));
    }

    @Test
    void lostSeatAtConfirmFailsTheBookingAndRefunds() {
        Checkout checkout = soloCheckout("G6");
        jdbc.update("UPDATE show_seats SET status = 'BLOCKED' WHERE show_id = ? AND seat_no = 'G6'", showId);   // should never happen

        pay(asha, checkout, "captured");

        assertEquals("SUCCESS", paymentStatus(checkout));
        assertEquals("SEAT_CONFLICT", refundReason(checkout));
        assertEquals("FAILED", jdbc.queryForObject("SELECT b.status FROM bookings b JOIN payments p ON p.booking_id = b.id WHERE p.gateway_order_id = ?", String.class, checkout.orderId()));
    }

    // ---------------------------------------------------------------- webhooks

    @Test
    void webhookNeedsAValidSignatureAndRunsOnce() {
        Checkout checkout = soloCheckout("G7");
        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_wh","order_id":"%s","amount":%d,"status":"captured"}}}}"""
                .formatted(checkout.orderId(), checkout.amountPaise());
        when(gateway.verifyWebhookSignature(body, "good")).thenReturn(true);
        when(gateway.fetchPayment("pay_wh")).thenReturn(gatewayPayment(checkout, "pay_wh", "captured", checkout.amountPaise()));

        String eventId = "evt_" + UUID.randomUUID();   // each run is a new event
        assertFalse(webhooks.handle(body, "bad", eventId), "bad signature rejected");
        assertEquals("CREATED", paymentStatus(checkout));

        assertTrue(webhooks.handle(body, "good", eventId));
        assertTrue(webhooks.handle(body, "good", eventId));   // Razorpay retried the same event
        assertEquals("SUCCESS", paymentStatus(checkout));
        verify(gateway, times(1)).fetchPayment("pay_wh");
    }

    // ---------------------------------------------------------------- helpers

    private Checkout soloCheckout(String seat) {
        UUID group = groups.create(asha, showId, true).groupId();
        BookingView locked = bookings.lock(asha, key(), new LockRequest(group, List.of(seat)));
        return payments.checkout(asha, key(), new CheckoutRequest(locked.id(), null));
    }

    private UUID twoPersonGroup() {
        UUID group = groups.create(asha, showId).groupId();
        groups.join(rahul, groups.view(asha, group).code());
        return group;
    }

    private void pay(UUID who, Checkout checkout, String status) {
        pay(who, checkout, status, checkout.amountPaise());
    }

    /** The browser's checkout succeeded (or failed) and the gateway reports it. */
    private void pay(UUID who, Checkout checkout, String status, long amount) {
        String gatewayPaymentId = "pay_" + checkout.orderId() + ("failed".equals(status) ? "_f" + seq.incrementAndGet() : "");
        when(gateway.fetchPayment(gatewayPaymentId)).thenReturn(gatewayPayment(checkout, gatewayPaymentId, status, amount));
        if ("failed".equals(status)) {
            long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE gateway_order_id = ?", Long.class, checkout.orderId());
            payments.settle(paymentId, gatewayPayment(checkout, gatewayPaymentId, status, amount));   // learnt via webhook/reconcile
        } else {
            payments.verify(who, new VerifyRequest(checkout.orderId(), gatewayPaymentId, "good-signature"));
        }
    }

    private static PaymentGateway.Payment gatewayPayment(Checkout c, String id, String status, long amount) {
        boolean failed = "failed".equals(status);
        return new PaymentGateway.Payment(id, c.orderId(), amount, status, "upi", "ab••@okaxis",
                failed ? "BAD_REQUEST_ERROR" : null, failed ? "Payment was declined by the bank" : null);
    }

    private String paymentStatus(Checkout c) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE gateway_order_id = ?", String.class, c.orderId());
    }

    private String refundReason(Checkout c) {
        return jdbc.queryForObject("SELECT r.reason FROM refunds r JOIN payments p ON p.id = r.payment_id WHERE p.gateway_order_id = ?", String.class, c.orderId());
    }

    private long baselineRefunds() {
        return refundRepository.count();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private void clearLocks() {
        var keys = redis.keys("lock:show:" + showId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private UUID user(String name) {
        User u = new User();
        u.setName(name.substring(0, 1).toUpperCase() + name.substring(1) + " Pay");
        u.setEmail("pay-test-" + name + "@example.com");
        u.setPasswordHash("hash");
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerifiedAt(Instant.now());
        return userRepository.save(u).getPublicId();
    }
}
