-- V4: Bookings (architecture.md §3-4, STORAGE.md "Groups and bookings", PAYMENT.md for the money split).
-- A booking is one attempt by a group to get seats: LOCKED (5 min) -> PAYMENT_PENDING -> CONFIRMED, or RELEASED/FAILED.
-- Seat LOCKS live in Redis; this table records the attempt and freezes the prices.
-- Status/mode/category columns hold Java enum names as text; no CHECK lists.

CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    booking_code VARCHAR(8) NOT NULL,               -- shown on tickets, e.g. TK7Q2M9A
    group_id BIGINT NOT NULL REFERENCES groups(id),
    item_type VARCHAR(20) NOT NULL,                 -- ItemType enum
    item_id BIGINT NOT NULL,                        -- no FK: the vertical validates it (ADR-007)
    status VARCHAR(20) NOT NULL DEFAULT 'LOCKED',   -- BookingStatus enum; 'LOCKED', 'PAYMENT_PENDING' and 'CONFIRMED' are used by the indexes below, never rename them
    mode VARCHAR(20) NULL,                          -- PaymentMode enum, set when payment starts
    total_paise BIGINT NOT NULL,                    -- every seat: ticket + fee + GST on fee
    paid_paise BIGINT NOT NULL DEFAULT 0,
    lock_expires_at TIMESTAMPTZ NOT NULL,
    payment_deadline TIMESTAMPTZ NULL,
    price_snapshot JSONB NOT NULL,                  -- {"PRIME": 28000, ...}: prices frozen at lock time
    version INT NOT NULL DEFAULT 0,                 -- optimistic lock: scheduler vs webhook can't both move it
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_booking_money CHECK (total_paise >= 0 AND paid_paise >= 0 AND paid_paise <= total_paise)
);
CREATE UNIQUE INDEX ux_booking_code ON bookings (booking_code);
-- One active attempt per group, and at most one success
CREATE UNIQUE INDEX ux_booking_active_per_group ON bookings (group_id) WHERE status IN ('LOCKED', 'PAYMENT_PENDING');
CREATE UNIQUE INDEX ux_booking_confirmed_per_group ON bookings (group_id) WHERE status = 'CONFIRMED';
CREATE INDEX idx_bookings_lock_expiry ON bookings (lock_expires_at) WHERE status = 'LOCKED';

-- One row per seat: who sits there and exactly what it costs (PAYMENT.md)
CREATE TABLE booking_units (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    unit_ref VARCHAR(10) NOT NULL,                  -- seat number, e.g. H7
    category VARCHAR(20) NOT NULL,                  -- SeatCategory enum name
    assignee_user_id BIGINT NULL REFERENCES users(id),
    ticket_price_paise BIGINT NOT NULL,             -- GST-inclusive, as cinemas show it
    ticket_base_paise BIGINT NOT NULL,
    ticket_gst_paise BIGINT NOT NULL,
    ticket_gst_rate_bp INT NOT NULL,                -- basis points: 1800 = 18%, 500 = 5%
    fee_paise BIGINT NOT NULL,
    fee_gst_paise BIGINT NOT NULL,
    CONSTRAINT ux_booking_unit UNIQUE (booking_id, unit_ref),
    CONSTRAINT ck_unit_tax CHECK (ticket_base_paise + ticket_gst_paise = ticket_price_paise),
    CONSTRAINT ck_unit_money CHECK (ticket_price_paise >= 0 AND fee_paise >= 0 AND fee_gst_paise >= 0)
);

-- A booked seat points at its booking (V2 left this for here)
ALTER TABLE show_seats ADD CONSTRAINT fk_show_seat_booking FOREIGN KEY (booking_id) REFERENCES bookings(id);

CREATE TRIGGER update_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Idempotency (ADR-010): one row per user action on a money-changing call (lock, split, pay...).
-- Written in the same transaction as the work, so a stored response always matches what happened.
CREATE TABLE idempotency_keys (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idem_key VARCHAR(64) NOT NULL,                 -- the client's Idempotency-Key header
    endpoint VARCHAR(100) NOT NULL,                -- e.g. "POST /bookings/lock"
    request_hash VARCHAR(64) NOT NULL,             -- SHA-256 of the request body: same key + other body = 422
    response_status INT NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,               -- created_at + 24 h; deleted by the cleanup job
    CONSTRAINT pk_idempotency PRIMARY KEY (user_id, idem_key)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_keys (expires_at);
