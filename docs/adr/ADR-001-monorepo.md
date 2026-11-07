# ADR-001: Use Monorepo

Status: Accepted | Date: 2026-11-07 | Author: Prince Kumar Sharma

## Context
Ticktit has backend, frontend, mobile, docs. Should we use 1 repo or 4 repos?

## Decision
Use 1 Monorepo: `ticktit/` with `backend/`, `frontend/`, `app/`, `docs/`.

## Alternatives Considered
- Polyrepo: 3 separate repos - Rejected: Hard to manage as solo dev, PRs across repos confusing, version mismatch.

## Consequences
- Positive: One clone = everything, one PR can change API + Frontend together, easy for interview demo.
- Negative: Repo grows big later. If team grows to 20 people, we may split.
