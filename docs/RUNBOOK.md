# Ticktit - Runbook - v4

How to run, reset and debug Ticktit. Keep this updated whenever setup changes.

## 1. Run Locally

Prerequisites: Java 21, Node 20+, Docker.

```bash
cp .env.example .env               # fill JWT_SECRET, QR_SECRET, MOCK_WEBHOOK_SECRET
docker compose up -d               # postgres, redis, kafka (KRaft), mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev
```

| Service | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui |
| Health | http://localhost:8080/actuator/health |
| Web app | http://localhost:5173 |
| Mailpit (emails, OTPs in dev) | http://localhost:8025 |

## 2. Seed Data

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,seed
```
Loads: cities (Siliguri, Kolkata, Patna), 2 theatres per city, 10 movies, 7 days of shows (09:30, 12:45, 16:00, 19:15, 22:30) with CLASSIC/PRIME/RECLINER prices, users `leader@ticktit.dev`, `aman@`, `rahul@`, `sneha@`, `late@`, `admin@`, `scanner@` (password in `.env`).

## 3. Demo Script (3 minutes)

1. Login as leader → Pushpa 2 → Siliguri 19:15 → Create Group → copy link.
2. Open the link in 2 incognito windows → join as Rahul and Sneha with email + password (login OTP shown in Mailpit).
3. Leader locks A1-A3 → seats turn yellow for everyone → Split Pay.
4. Each pays with `success@mock` → green ticks → CONFIRMED → QR on screen + email in Mailpit.
5. Second group: `late@` never pays → wait for the deadline (set `payment.window=2m` for demo) → FAILED → refunds visible.
6. Open `/scan` as scanner → scan the group QR → green; scan again → "Already checked in".

## 4. Reset

```bash
docker compose down -v && docker compose up -d     # wipes DB, Redis, Kafka
redis-cli --scan --pattern 'lock:show:*' | xargs -r redis-cli del   # clear stuck locks only
```

## 5. Common Problems

| Symptom | Check | Fix |
|---|---|---|
| Lock always 503 | `/actuator/health` Redis DOWN | `docker compose restart redis` |
| Emails not arriving | Outbox lag metric; `outbox_events WHERE published_at IS NULL` | Kafka down? Restart Kafka; relay catches up automatically |
| Payment stuck PENDING | `payments` row + `webhook_events` | Mock: call `/mock-gateway/orders/{id}/complete`; reconciliation runs every 5 min |
| Refund FAILED | `refunds.attempts`, logs with `requestId` | Admin → Retry refund |
| Seat shows LOCKED but no group | Redis key `lock:show:{id}:seat:{no}` TTL | Wait for TTL or delete the key (dev only) |
| Booking never expires | Scheduler logs; ShedLock table if multiple instances | Restart app; check `scheduler.enabled=true` |

## 6. Config Reference
`lock.ttl`, `payment.window`, `payment.nudge-at`, `payment.cover-prompt-at`, `group.max-members`, `booking.max-seats`, `otp.*`, `login.fail-limit`, `poll.interval` - see SRS.md §1.1.
