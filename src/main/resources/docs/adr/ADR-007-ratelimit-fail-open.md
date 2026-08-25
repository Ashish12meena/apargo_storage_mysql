# ADR-007 — Rate limiting fails open

**Status:** Accepted · 2026-08-20

## Context

Rate limiting moves from in-JVM buckets to Redis, because the current per-JVM
limiter gives N× the configured limit across N replicas and produces 429s that
depend on load-balancer routing. That introduces a new dependency on the request
path. What happens when Redis is unavailable?

## Decision

**Fail open**, with a metric and a P2 alert.

## Consequences

- A Redis outage degrades protection rather than causing an outage.
- During the window, a tenant could exceed their limit. Quota still bounds storage
  consumption, so the blast radius is request volume, not data.
- `storage.ratelimit.degraded` is alertable, so fail-open is visible rather than
  silent.
- Redis is deliberately **excluded from the readiness probe** — otherwise a Redis
  outage removes the whole fleet from rotation, converting a degradation into the
  outage this decision exists to avoid.

## Alternatives rejected

**Fail closed.** A rate limiter outage becomes a total outage. The limiter protects
against abuse, and abuse is less likely than infrastructure failure.

**Fall back to in-memory buckets.** Sounds appealing, and reintroduces exactly the
per-JVM inconsistency being fixed — with the added confusion of behaviour silently
changing mid-incident.
