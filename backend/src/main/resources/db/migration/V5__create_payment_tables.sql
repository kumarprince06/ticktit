-- V5: Payments and refunds (PAYMENT.md, REFUND.md, ADR-010, ADR-011). Gateway: Razorpay (test mode in dev).
-- Never stored: card number, CVV, UPI PIN. Only gateway ids, amounts, method and status.
-- Status/mode/reason columns hold Java enum names as text; no CHECK lists.

-- One row per pay attempt = one Razorpay order. The payer pays their share (SPLIT) or everything left (PAY_FOR_ALL).
CREATE TABLE payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    payer_user_id BIGINT NOT NULL REFERENCES users(id),
    covers VARCHAR(20) NOT NULL,                    -- PaymentCovers enum: OWN_SHARE / EVERYTHING_LEFT
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',  -- PaymentStatus enum
    idempotency_key VARCHAR(64) NOT NULL,
    gateway VARCHAR(20) NOT NULL,                   -- e.g. RAZORPAY
    gateway_order_id VARCHAR(64) NOT NULL,          -- order_XXXX
    gateway_payment_id VARCHAR(64) NULL,            -- pay_XXXX, set when it succeeds
    method VARCHAR(20) NULL,                        -- upi / card / netbanking / wallet
    method_detail VARCHAR(100) NULL,                -- masked: "VISA ••4242", "HDFC", "ab••@okaxis"
    failure_code VARCHAR(100) NULL,
    failure_reason VARCHAR(255) NULL,
    succeeded_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_amount CHECK (amount_paise > 0),
    CONSTRAINT ux_payment_idem UNIQUE (payer_user_id, idempotency_key)
);
-- One gateway order or payment can never be counted twice
CREATE UNIQUE INDEX ux_payment_order ON payments (gateway_order_id);
CREATE UNIQUE INDEX ux_payment_gateway_payment ON payments (gateway_payment_id) WHERE gateway_payment_id IS NOT NULL;
CREATE INDEX idx_payments_booking ON payments (booking_id);
CREATE INDEX idx_payments_open ON payments (created_at) WHERE status = 'CREATED';

-- Every try at the gateway, including failed card/UPI attempts on the same order: what support needs to answer "why did my payment fail?"
CREATE TABLE payment_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    gateway_payment_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,                    -- the gateway's word: created / authorized / captured / failed / refunded
    amount_paise BIGINT NOT NULL,
    method VARCHAR(20) NULL,
    method_detail VARCHAR(100) NULL,
    error_code VARCHAR(100) NULL,
    error_description VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_attempt_gateway_payment UNIQUE (gateway_payment_id)
);
CREATE INDEX idx_attempts_payment ON payment_attempts (payment_id);

-- One refund per successful payment in MVP (REFUND.md)
CREATE TABLE refunds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    amount_paise BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL,                    -- RefundReason enum
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',-- RefundStatus enum
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gateway_refund_id VARCHAR(64) NULL,
    last_error VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_refund_amount CHECK (amount_paise > 0),
    CONSTRAINT ux_refund_payment UNIQUE (payment_id)
);
CREATE UNIQUE INDEX ux_refund_gateway ON refunds (gateway_refund_id) WHERE gateway_refund_id IS NOT NULL;
CREATE INDEX idx_refunds_due ON refunds (next_attempt_at) WHERE status IN ('INITIATED', 'PROCESSING');

-- Every webhook received, verified or not, kept for audit; each event is processed once
CREATE TABLE webhook_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    gateway_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ NULL,
    error VARCHAR(255) NULL,
    CONSTRAINT ux_webhook UNIQUE (provider, gateway_event_id)
);

-- GST invoices must be numbered without gaps per financial year: TT/2026-27/000001
CREATE TABLE invoice_counters (
    financial_year VARCHAR(7) PRIMARY KEY,          -- '2026-27'
    next_no BIGINT NOT NULL DEFAULT 1
);

-- Filled when a booking is confirmed
ALTER TABLE bookings ADD COLUMN invoice_no VARCHAR(30) NULL;
ALTER TABLE bookings ADD COLUMN confirmed_at TIMESTAMPTZ NULL;
ALTER TABLE bookings ADD COLUMN contact_email VARCHAR(255) NULL;
ALTER TABLE bookings ADD COLUMN contact_phone VARCHAR(13) NULL;
CREATE UNIQUE INDEX ux_booking_invoice ON bookings (invoice_no) WHERE invoice_no IS NOT NULL;
CREATE INDEX idx_bookings_payment_deadline ON bookings (payment_deadline) WHERE status = 'PAYMENT_PENDING';

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_attempts_updated_at BEFORE UPDATE ON payment_attempts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_refunds_updated_at BEFORE UPDATE ON refunds FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Per seat: when it was paid for (by its own person under Split Pay, or by the organiser under Pay For All)
ALTER TABLE booking_units ADD COLUMN paid_at TIMESTAMPTZ NULL;
