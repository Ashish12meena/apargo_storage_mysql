# ADR-004 — Control plane: bytes bypass the service

**Status:** Accepted · 2026-08-20 · **revised 2026-08-22** (see Revision)

## Context

Today every uploaded byte passes through the JVM heap, a temp file on local disk,
and the servlet container. On the local provider there is a second full copy from
temp to final location. Downloads in local mode stream through the service too.

Consequences: throughput is a function of one pod's disk rather than S3; a 500 MB
upload holds a request thread for minutes; memory scales with concurrency × buffer.

## Decision

The service becomes a **control plane**: metadata, quota, authorization, lifecycle.
S3 and CloudFront are the **data plane**.

- Uploads above a threshold (10 MB) use presigned PUT — client to S3, directly.
- Uploads below it are still proxied, for single-round-trip convenience, but
  **stream** rather than spooling to a temp file. Size is known from the multipart
  header, so there is no reason to write the file twice.
- All reads use presigned GET or CloudFront. Never proxied.

## Consequences

- Request duration decouples from file size.
- The service becomes genuinely stateless — no local disk, no temp files.
- Upload becomes a **three-call protocol** (initiate → PUT → complete), which is more
  complex for clients than one multipart POST. The proxied path remains for the
  common small-file case, so most callers never see the complexity.
- Content inspection must happen at **completion** rather than in flight, since a
  presigned PUT cannot be intercepted. The record is not `ACTIVE`, and therefore not
  readable, until inspection passes.
- Requires the presigned URL to be tightly scoped: fixed key, exact
  `content-length-range`, `Content-Type` condition, 15-minute expiry, single use.

## Alternatives rejected

**Keep proxying everything.** Simplest client contract, but the throughput ceiling
is the pod. Directly contradicts NFR-11 and NFR-13.

**Proxy with streaming only, no presigned.** Removes the temp file but not the
bytes. A 5 GB upload still occupies a thread for its whole duration.


---

## Revision — 2026-08-22

Confirmed workload: WhatsApp media around **16 MB**, application files smaller.
This is a general-purpose **small-file** storage service.

At that size the original argument does not hold. A 16 MB proxied upload is a
single round trip that occupies a request thread briefly; it does not make request
duration a function of file size in any way that matters. The three-call protocol
(initiate → PUT → complete) is real complexity for the client, and at 16 MB it buys
nothing.

**Direct-to-storage upload is therefore DISABLED by default:**

```yaml
storage.presigned-upload:
  enabled: false
  threshold-bytes: 33554432    # 32 MB
```

What stays regardless, because it is not about presigned uploads at all:

- **`upload_session` two-phase reservation.** This exists for crash safety — quota
  charged before bytes are written, sweeper reclaims anything abandoned. It applies
  identically to the proxied path.
- **Reads still bypass the service.** CloudFront and presigned GET are unchanged;
  download was never the concern.

The presign machinery remains wired and tested-by-inspection, so raising a limit
for one tenant stays a YAML flip rather than a re-architecture. If it is still
unused after Phase 3, delete it — carrying unused code is its own cost.
