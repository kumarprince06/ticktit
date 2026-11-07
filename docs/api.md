# Ticktit API Contract - v1

Base URL: `/api/v1`

## Auth
- `POST /auth/register` - Register user
- `POST /auth/login` - Login, returns JWT

## Movies & Shows
- `GET /movies` - List movies
- `GET /movies/{id}` - Movie detail
- `GET /shows?movieId={id}&city={city}` - Shows for movie
- `GET /shows/{id}/seats` - Seat layout + availability

## Groups
- `POST /groups` - Create group (leader)
- `POST /groups/{code}/join` - Join group via code
- `GET /groups/{id}` - Group detail + members + selected seats

## Bookings
- `POST /bookings/lock` - Lock seats for 5 min (Redis)
- `POST /bookings/confirm` - Confirm booking after payment
- `GET /bookings/{id}` - Booking detail
- `GET /bookings/my` - My bookings

## Payments
- `POST /payments/split` - Create split payment links
- `POST /payments/webhook` - Payment gateway webhook
