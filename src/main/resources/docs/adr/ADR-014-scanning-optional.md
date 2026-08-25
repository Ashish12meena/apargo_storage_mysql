# ADR-014 — Malware scanning is a wired capability, disabled by default

**Status:** Accepted · 2026-08-22

## Context

Callers are trusted internal services (`template-service`, the chat system), but
the bytes originate from end users choosing files on their phones. Trusting the
caller and trusting the payload are different decisions.

Scanning every upload has a real operational cost — a scanner to run, keep updated,
and monitor — that not every deployment will want to pay.

## Decision

The scanning **capability is always wired**; the scanner is selected and switched
by configuration.

```yaml
media.scanning:
  enabled: false
  provider: noop            # noop | clamav
  block-download-until-scanned: false
```

- `MalwareScannerPort` exists in the application layer regardless.
- `NoOpMalwareScanner` is the default and reports `SKIPPED`.
- `MediaScanHandler` consumes `media.created` from the outbox and is a no-op when
  disabled, so the event flow is identical either way.
- `scan_status` shipped in `V2`, so enabling scanning needs **no migration**.

## Consequences

- Turning scanning on is a YAML flip plus one adapter. No schema change, no
  lifecycle change, no API change, no code path that only exists when enabled.
- Scanning is **never** in the request path. A slow or unavailable scanner cannot
  fail an upload.
- `INFECTED` routes through `Media.recordScanResult`, so the state machine — not
  the handler — decides that infected means `QUARANTINED`.
- `block-download-until-scanned` is separate and also off by default: it is
  stricter, but it makes scan latency visible to every reader, so it is a
  deployment policy rather than a default.
- **Startup fails if `enabled: true` while `provider: noop`.** Scanning switched on
  with nothing behind it is worse than scanning switched off, because every file
  appears to have passed a scan.

## Alternatives rejected

**Remove scanning entirely.** Cheapest, and it makes adding it later a schema
migration plus a lifecycle change rather than a config flip.

**Always on.** Imposes an operational dependency on deployments that do not want it,
for a service whose callers are internal.
