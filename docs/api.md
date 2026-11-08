# Ticktit - API Contract - v3 Final

- Base URL: `http://localhost:8080/api/v1`
- Auth: header `Authorization: Bearer <JWT>` on every endpoint except `/auth/*` and `GET /movies*`. Missing/invalid JWT -> `401`.
- The user is always taken from the JWT, never from the request body.

## Auth

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| POST | `/auth/register` | `{name, email, phone, password}` | `201 {userId, token}` / `409` Email already exists |
| POST | `/auth/login` | `{email, password}` | `200 {token, refreshToken, user}` / `401` / `423` Account locked |
| POST | `/auth/refresh` | `{refreshToken}` | `200 {token}` |
| POST | `/auth/logout` | - | `200` |

## Movies and Shows

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | `/movies?city=Siliguri&language=Hindi&genre=Action&page=0` | - | `200 [{id, title, posterUrl, rating}]` (20 per page) |
| GET | `/movies/{id}` | - | `200 {id, title, posterUrl, duration, rating, synopsis, cast}` |
| GET | `/shows?movieId=1&city=Siliguri&date=2026-11-08` | - | `200 [{id, theatre, screen, time, price, availableSeats, totalSeats}]` |
| GET | `/shows/{id}/seats` | - | `200 [{seatNo, status, lockedUntil}]` - status `AVAILABLE`/`LOCKED`/`BOOKED` |

## Groups

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| POST | `/groups` | `{showId}` | `201 {groupId, code, shareLink}` e.g. code `TICK12` |
| POST | `/groups/{code}/join` | - | `200 {groupId, members}` / `400` Group is full (max 8) / `409` Group already booked or cancelled |
| GET | `/groups/{id}` | - | `200 {group, members: [{userId, name, status}]}` - status `JOINED`/`PAID`/`PENDING` |
| DELETE | `/groups/{id}/members/{userId}` | - | `200` (Leader only; auto-refund if member already PAID) |
| DELETE | `/groups/{id}` | - | `200` (Leader only; only if no payment yet) |

## Bookings

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| POST | `/bookings/lock` | `{groupId, seats: ["A1", "A2"]}` | `200 {bookingId, lockedUntil}` / `400` Too many seats / `403` Not leader / `409` Seats already locked (all or nothing) |
| POST | `/bookings/unlock` | `{groupId}` | `200` (Leader only) |
| GET | `/bookings/{id}` | - | `200 {booking, seats, members}` - includes payment status per member |
| GET | `/bookings/my` | - | `200 [{booking}]` - upcoming + past, newest first |

## Payments
All payment calls need header `Idempotency-Key: <uuid>`. The same key returns the previous result and never charges twice. The server computes the amount from the DB, so the client never sends it.

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| POST | `/payments/split` | `{groupId}` | `201 [{paymentId, userId, amount, status: PENDING}]` (Leader only) |
| POST | `/payments/pay` | `{groupId}` | `200 {paymentId, status}` - `SUCCESS`/`FAILED` |
| POST | `/payments/pay-for-all` | `{groupId}` | `200 {paymentId, status}` (Leader only) |

## Errors
Body: `{code, message}`.

| Status | Meaning |
| --- | --- |
| 400 | Bad request / validation failed |
| 401 | Missing or invalid JWT |
| 403 | Not allowed (e.g. not the group leader) |
| 404 | Not found |
| 409 | Conflict - seat locked, email exists, group booked |
| 423 | Account locked after 5 failed logins |
