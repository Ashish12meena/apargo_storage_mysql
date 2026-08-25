# ADR-002 — One upload implementation

**Status:** Accepted · 2026-08-20

## Context

Two fully-implemented upload orchestrators exist simultaneously:

- `MediaUploadOrchestrator.uploadMedia()` — pessimistic locking, single
  `@Transactional`, live WhatsApp push, ThreadLocal tenant context. **Not wired to
  any controller.**
- `ConcurrentMediaUploadService.uploadMediaSync()` — optimistic locking, WhatsApp
  push commented out, explicit tenant parameters. **Wired.**

The unwired one compiles, has tests, and has javadoc explaining its design
decisions. Dead code that looks alive is worse than dead code that looks dead: the
next engineer will assume it is the one in use and fix a bug in it.

## Decision

**One `UploadMediaUseCase`.** Keep the explicit-parameter tenant context from
`ConcurrentMediaUploadService`; delete `MediaUploadOrchestrator.uploadMedia()`.

Two *modes* (proxied, presigned) behind one port. Modes differ only in who moves
the bytes; quota, validation, and lifecycle are identical.

## Consequences

- One place to fix an upload bug.
- Explicit tenant parameters mean async execution cannot lose tenant scope by
  reading an empty ThreadLocal — the failure that forced two implementations.
- **`MediaUploadOrchestrator` cannot simply be deleted.** `MediaController` uses it
  for `getMedia`, `getMediaByType`, and `getPublicUrl`, so it is half-live. Its read
  methods migrate to `MediaQueryService` first. This is a real trap and is called
  out in Phase 2 step 2.18.

## Alternatives rejected

**Keep both, document the difference.** The current state already has documentation
explaining both, and it did not prevent the confusion.

**Keep the pessimistic one.** It serialises uploads per tenant and requires an
enclosing transaction (`Propagation.MANDATORY`), which forces the storage write
inside the transaction — the exact thing [05 §7](../05-domain-design.md) forbids.
