# ADR-010: Idempotency Keys per Attempt

Status: Accepted | Date: 2026-11-11 | Author: Prince Kumar Sharma

## Context
v3 generated one idempotency key per member and stored it in localStorage. A double-click is safe, but if the first attempt FAILED, every retry returns the same FAILED result, so the member can never pay. The key also did not protect against reusing it with a different body.

## Decision
- Client sends `Idempotency-Key: <uuid>` on every money-changing call: lock, split, pay, pay-for-all, cover, cancel, refund retry.
- One key per **user action / attempt**. Network retries and double-clicks reuse it; a new Pay tap after a FAILED attempt uses a new key.
- Server stores `idempotency_keys (user_id, idem_key, endpoint, request_hash, response_status, response_body, expires_at = +24h)` in the same transaction as the work.
- Same key + same request hash → stored response. Same key + different hash → `422 IDEMPOTENCY_KEY_REUSED`. Key still in progress → `409` (client retries shortly).
- Payments also have `UNIQUE (payer_user_id, idempotency_key)`, `UNIQUE gateway_order_id`, `UNIQUE gateway_txn_id`.

## Alternatives Considered
- One key per member (v3) - rejected: blocks retries after failure.
- No keys, rely on "one PENDING payment per member" - rejected: does not cover lock/split/cancel and gives poor error messages.

## Consequences
- Positive: double-click, retry and refresh are all safe; retry after failure works.
- Negative: one more table; clients must generate and keep keys for the duration of an attempt.
