# ADR-011: Async Payment Gateway Interface (Mock now, Razorpay later)

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma

## Context
v3's mock gateway returned SUCCESS/FAILURE synchronously and failed on "amount 999". Real UPI/card payments are asynchronous: create order → customer pays in app/bank → gateway sends a webhook. A synchronous mock would force a rewrite when Razorpay arrives. Also, the server computes the amount, so testers cannot choose 999.

## Decision
- One interface used by core payment code:
  ```java
  interface PaymentGateway {
      GatewayOrder createOrder(long amountPaise, String reference);
      GatewayPayment fetchPayment(String gatewayOrderId);   // reconciliation
      GatewayRefund refund(String gatewayTxnId, long amountPaise, String reference);
      WebhookEvent verifyAndParse(String rawBody, String signature);
  }
  ```
- Payment state changes to SUCCESS/FAILED **only** from a verified webhook or `fetchPayment`, never from the browser.
- `MockGateway` (MVP): `POST /mock-gateway/orders/{id}/complete {vpa}` (dev profile only) fires a signed webhook after ~1 s. `success@mock` → SUCCESS, `failure@mock` → FAILED, `timeout@mock` → no webhook.
- `RazorpayGateway` (Phase 2): same interface, Razorpay test mode first (`success@razorpay` / `failure@razorpay`).
- Reconciliation job every 5 min calls `fetchPayment` for PENDING payments older than 2 min.

## Alternatives Considered
- Synchronous mock - rejected: different shape from real gateways.
- Integrate Razorpay now - rejected for MVP: KYC/bank account needed; test mode is fine for Phase 2.

## Consequences
- Positive: the MVP behaves like production (pending → webhook), all failure scenarios are testable, switching gateways is an adapter change.
- Negative: slightly more code in MVP (webhook endpoint, signature, reconciliation).
