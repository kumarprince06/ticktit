# ADR-006: Use Spring Boot 3 + Java 21 for Backend

Status: Accepted | Date: 2026-11-07

## Context
Need backend for money + concurrency. Must be reliable, have good transaction support.

## Decision
Spring Boot 3, Java 21, Spring Data JPA, Spring Security.

## Alternatives Considered
- Node.js + Express: Good for fast MVP, but weak for transactions, easy to make mistake with concurrent seat lock. Also Prince's work stack is Java.
- Python + Django: Good, but slower for high concurrency, less type safety for payment logic.

## Consequences
- Positive: Strong typing, @Transactional guarantees no half-booking, huge community, best for resume / job (Java + Spring is in demand in India).
- Negative: Slower to start than Node, more boilerplate.
