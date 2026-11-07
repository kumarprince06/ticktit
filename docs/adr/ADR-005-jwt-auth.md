# ADR-005: Use JWT for Authentication

Status: Accepted | Date: 2026-11-07

## Context
Frontend (React) and Backend (Spring Boot) are separate. Need stateless auth that works for web + mobile later.

## Decision
JWT (JSON Web Token) - Access token 24h, stored in HttpOnly Cookie (not LocalStorage).

## Alternatives Considered
- Session + Cookie in DB: Rejected - Need DB lookup for every request, not scalable, hard for mobile.
- OAuth only (Google Login): Rejected - Good for Phase 2, but MVP needs Email/Password first.

## Consequences
- Positive: Stateless, no DB lookup, works for web + mobile, easy to scale.
- Negative: Token cannot be revoked instantly. Mitigation: Short expiry + Refresh token in Phase 2.
