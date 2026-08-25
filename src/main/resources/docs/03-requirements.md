# 03 — Functional and Non-Functional Requirements

Every requirement has an ID. Contracts, phases, and the definition of done all
reference these IDs, so nothing gets built without a stated reason.

## 1. Functional requirements

### Upload

| ID | Requirement | Phase |
|---|---|---|
| FR-01 | Upload a single file through the service (proxied), receiving metadata and a download URL | 2 |
| FR-02 | Initiate a direct-to-storage upload and receive presigned URL(s) | 3 |
| FR-03 | Confirm a direct upload; the record only becomes readable after confirmation | 3 |
| FR-04 | Abandon an initiated upload, releasing quota | 3 |
| FR-05 | Upload a bounded batch, with per-file success/failure and no all-or-nothing failure | 2 |
| FR-06 | Reject an upload that would breach org or project quota | 2 |
| FR-07 | Reject a file whose actual content type does not match its declared type | 2 |
| FR-08 | Retrying an upload with the same `Idempotency-Key` produces one file and one quota charge | 2 |

### Retrieval

| ID | Requirement | Phase |
|---|---|---|
| FR-10 | Fetch metadata for a single media item by id | 2 |
| FR-11 | List a tenant's media with keyset pagination, filterable by type | 2 |
| FR-12 | Obtain a short-lived, tenant-scoped download URL by media id | 2 |
| FR-13 | Stream a file with HTTP `Range` support (local provider) | 3 |
| FR-14 | Conditional GET via `ETag` / `If-None-Match` | 3 |

### Deletion

| ID | Requirement | Phase |
|---|---|---|
| FR-20 | Soft-delete a media item, releasing quota immediately | 2 |
| FR-21 | Physically remove the stored object after deletion, asynchronously and retryably | 2 |
| FR-22 | Restore a soft-deleted item within the grace period | 2 |
| FR-23 | Permanently erase an item, skipping the grace period, under a distinct scope | 2 |
| FR-24 | Bulk-delete by id list, with per-item results | 3 |
| FR-25 | Delete all media for a project or org (internal), releasing quota, asynchronously | 2 |

### Quota

| ID | Requirement | Phase |
|---|---|---|
| FR-30 | Provision or update org and project quota limits (internal, idempotent) | 2 |
| FR-30a | **A project limit may never exceed its organisation's total** — rejected at provisioning with a distinct error | 2 |
| FR-30b | Lowering an org limit below an existing project limit is rejected, not silently clamped | 2 |
| FR-30c | Whether project limits may *sum* above the org total is configurable (`quota.allow-project-overcommit`) | 2 |
| FR-31 | A tenant can read its own quota and utilisation | 2 |
| FR-32 | Reservation is atomic and correct under concurrent uploads | 2 |
| FR-33 | Abandoned or failed uploads release reserved quota automatically | 2 |
| FR-34 | Drift between recorded and actual usage is detected, corrected, and alerted on | 2 |
| FR-35 | Crossing 80% and 95% utilisation emits an event | 3 |

### Lifecycle and consistency

| ID | Requirement | Phase |
|---|---|---|
| FR-40 | Objects in storage with no database row are detected and reclaimed | 3 |
| FR-41 | Expired upload sessions are swept and their quota reclaimed | 2 |
| FR-42 | Every state-changing operation is recorded in an append-only audit trail | 2 |
| FR-43 | Malware scanning is available behind a feature flag; when enabled, infected files are quarantined asynchronously | 3 |
| FR-44 | Per-file size limits and MIME allowlists are configurable without a code change (global, YAML) | 2 |

## 2. Non-functional requirements

### Performance

| ID | Requirement | Target |
|---|---|---|
| NFR-01 | Metadata read latency | p95 < 100 ms, p99 < 250 ms |
| NFR-02 | Upload initiate (presign) latency | p95 < 150 ms |
| NFR-03 | Proxied upload latency, 5 MB | p95 < 2 s |
| NFR-04 | Quota reservation latency | p95 < 20 ms |
| NFR-05 | Listing latency at any page depth | p95 < 200 ms — the reason for keyset pagination |

### Scale

| ID | Requirement | Target |
|---|---|---|
| NFR-10 | Media rows | 50 M without redesign; partition plan ready beyond |
| NFR-11 | Sustained upload rate | 500/s across the cluster, at ~16 MB or smaller |
| NFR-12 | Concurrent tenants | 10,000 orgs |
| NFR-13 | Max single file | 64 MB absolute ceiling; 16 MB default profile |
| NFR-14 | Horizontal scaling | Linear to 20 replicas, no shared mutable state |

### Availability and reliability

| ID | Requirement | Target |
|---|---|---|
| NFR-20 | Control-plane availability | 99.9% monthly |
| NFR-21 | Read availability | 99.95% — CDN reads survive a control-plane outage |
| NFR-22 | Durability | Inherited from S3 (11 nines) |
| NFR-23 | Quota accuracy | Exact under concurrency; drift converges within 24 h |
| NFR-24 | **No acknowledged upload is ever lost** | Absolute |
| NFR-25 | **No object exists without a database row** | Absolute — see [05 §7](05-domain-design.md) |
| NFR-26 | Recovery from an accidental delete | 7-day grace period |

### Security

| ID | Requirement |
|---|---|
| NFR-30 | Tenant identity derives ONLY from a cryptographically verified credential |
| NFR-31 | Cross-tenant access is impossible even with knowledge of a storage key |
| NFR-32 | No credential appears in source, configuration, logs, or error responses |
| NFR-33 | Every state change is attributable to a specific principal |
| NFR-34 | Content is verified by inspection, never by client declaration |
| NFR-35 | Encrypted in transit (TLS 1.3) and at rest (SSE-KMS) |

### Operability

| ID | Requirement |
|---|---|
| NFR-40 | Every request is traceable end to end by one identifier |
| NFR-41 | Storage backend reachability is surfaced by a health check, not inferred |
| NFR-42 | Deploys are zero-downtime; in-flight uploads drain |
| NFR-43 | Every alert has a runbook |
| NFR-44 | Configuration errors fail at startup, not at first request |

## 3. Explicit non-requirements

Stated so nobody builds them speculatively:

- Image or video transcoding, thumbnails, previews
- Structural file analysis — pixel counts, page counts, archive inspection (ADR-013)
- Large-file storage. This is a small-file service; the ceiling is 64 MB
- Full-text search over documents
- File versioning visible to tenants (S3 versioning is a recovery mechanism only)
- Cross-region replication
- Public, unauthenticated file sharing
- Cross-tenant deduplication — see [ADR-011](adr/ADR-011-no-dedup.md)
- Real-time collaborative editing
