# Ticktit - Test Plan - v4

Goal: prove the three promises with automated tests - **0 double booking, 0 double charge, 0 lost refunds** - plus every scenario in SCENARIOS.md.

## 1. Test Levels

| Level | Tools | Covers | Exit criteria |
|---|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Price model (paise, GST slabs, fee), shares by seat, state machine transitions, invite code generator, QR token sign/verify, alternative-block algorithm | All rules in PAYMENT.md / ADR-009 covered; ≥ 70% service-layer coverage |
| Integration | Spring Boot Test + Testcontainers (Postgres, Redis, Kafka) | Repositories, constraints, Lua lock script, conditional confirm update, outbox relay → consumer, idempotency table, scheduler | Every constraint in db-design.md has a test that proves it rejects bad data |
| Concurrency | Testcontainers + `ExecutorService` / `CountDownLatch` | 100 parallel locks on one seat; webhook + scheduler racing on one booking; duplicate webhooks; double-click pay | Exactly 1 winner; no duplicate payment/refund rows; booking ends in one valid state |
| API | MockMvc / REST Assured | Status codes, Problem Details `code`, auth per endpoint, leader-only rules | Every endpoint in api.md |
| Webhook | Recorded mock payloads | Valid, bad signature, duplicate, out-of-order (FAILED after SUCCESS), late payment | Bad signature → 400 and no change |
| End-to-end | Playwright | S1 (4 friends pay), S5 (one friend never pays → refunds), S2 (Pay For All) | Green in CI |
| Load | k6 | Lock endpoint and seat map at 100-200 virtual users | NFR-01 met; screenshot in README |
| Security | OWASP Dependency-Check, manual checklist from THREAT-MODEL.md | Vulnerable libs, IDOR on group/booking IDs, auth bypass | No open high-severity issue |

## 2. Must-Have Tests (map to scenarios)

| Test | Scenario |
|---|---|
| `lock_sameSeat_100Threads_exactlyOneWins` | S6, EC-01 |
| `confirm_conditionalUpdate_rejectsAlreadyBookedSeat` | F8 |
| `pay_sameIdempotencyKeyTwice_returnsSamePayment` | S7, EC-02 |
| `pay_newKeyAfterFailure_allowsRetry` | S11 |
| `webhook_duplicateEvent_ignored` | S18 |
| `deadline_passed_failsBookingAndRefundsPaidMembers` | S5 |
| `latePayment_afterFailed_isRefunded` | S17 |
| `removePaidMember_refundsAndUnassignsSeat` | S8 |
| `showCancelled_refundsIncludingFee` | S12 |
| `redisDown_lockReturns503` | S19 |
| `kafkaDown_bookingConfirms_eventsPublishedLater` | S20 |
| `qr_scanTwice_secondIs409` / `qr_forged_rejected` | S21, S22 |
| `share_sumOfAssignedSeats_equalsTotal` | ADR-009 |
| `gst_slab_5percentAt100_18percentAbove` | PAYMENT.md §1 |
| `join_whilePaymentPending_409` | S16 |
| `login_tooManyFailures_429_noAccountLeak` | S10 |

## 3. Test Data
Seed profile `test`: 1 city, 1 theatre, 1 show with 3 categories, users `leader@`, `aman@`, `rahul@`, `sneha@`, `late@` (never pays). Mock gateway VPAs: `success@mock`, `failure@mock`, `timeout@mock`.

## 4. CI (GitHub Actions on every PR)
Build → unit → integration (Testcontainers) → API → OWASP dependency check → Docker image build. E2E and k6 run nightly or before a demo.

## 5. Definition of Done
Code + tests merged via PR, SRS/scenario ID in the commit message, docs updated if behaviour changed, CI green.
