# ADR-009 — WhatsApp media publishing does not belong in this service

**Status:** **Proposed** — needs WABA team agreement (OD-1, BL-4)

## Context

The current service calls `waba-service` and then the Meta Graph API
**synchronously, inline, in the upload path**, with a 60-second read timeout. It
carries an `X-Waba-Id` header on the upload endpoint, holds Meta credentials, and
maintains Facebook-specific Resilience4j configuration.

In the wired upload service the same call is merely commented out — one uncomment
from returning to the hot path.

The service is called `storage`. None of this is storage.

## Decision (proposed)

Remove WhatsApp publishing entirely. This service emits `media.created`.
`waba-service` subscribes and performs its own push, fetching bytes via a presigned
URL.

## Consequences

- Deletes an entire integration package, the Meta client, WABA credential handling,
  the `X-Waba-Id` header, and a block of resilience config from this service.
- Upload latency and availability stop depending on a third party.
- WhatsApp logic lives with WhatsApp expertise, so Meta API changes stop being this
  team's problem.
- **Costs one extra hop**: `waba-service` fetches bytes from S3 rather than
  receiving them in-process. That hop is S3-to-service inside one region — cheap,
  and it buys a clean ownership boundary.
- Requires `waba-service` to consume events, which it may not do today.

## Interim

If not accepted, the push moves behind the outbox in Phase 3 — asynchronous,
retryable, dead-lettered. That fixes the latency coupling but leaves the
responsibility in the wrong service.

## Alternatives rejected

**Keep it synchronous.** Ties upload availability to Meta's.

**Keep it async but in this service (the interim).** Better than today, and still
leaves WhatsApp domain logic and credentials in a storage service.
