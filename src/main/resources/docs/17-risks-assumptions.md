# 17 — Risks and Assumptions

**Read this first if you are new to the service.** Several assumptions below are
unverified, and the plan depends on them.

## 1. Assumptions

Each is stated so it can be **checked** rather than inherited.

| ID | Assumption | If wrong |
|---|---|---|
| **A-1** | Every caller is a trusted internal service. **Confirmed — the auth model depends on it.** | If a browser, partner, or customer-run system ever calls this service, the API key model is insufficient and JWT must be reinstated (ADR-010). |
| **A-2** | All tenant-facing traffic reaches this service through the gateway | Already assumed today and already false in a Eureka mesh — which is why ADR-010 exists. The design does not depend on it holding. |
| **A-3** | `template-service` is the only consumer persisting `/serve/**` URLs | Any other consumer that persists them is also frozen to that path. **Audit before Phase 3.** |
| **A-4** | Org and project ids are stable and never reused | Reuse would make a new project inherit a deleted one's media. Confirm with the org service team. |
| **A-5** | S3 is available in every target region and environment | MinIO covers dev; a region without S3 would need a different backend. |
| **A-6** | `MediaType.PRODUCT` is unused | It has no upload path, no validation rule, and no consumer in this codebase. Remove in Phase 2 unless someone objects (OD-7). |
| **A-7** | 500 uploads/s is a realistic ceiling for the next 12 months | If actual demand is 10×, enable presigned upload; quota reservation would need §12.4's escape hatches. |
| **A-11** | Files are small — ~16 MB at the top end, usually less. **Confirmed by the product owner.** | A genuine large-file requirement means enabling `storage.presigned-upload`, which is already wired. |
| **A-12** | Every tenant accepts the same per-file size limit; only total capacity differs. **Confirmed by the product owner.** | If one tenant needs a different per-file limit, add `limit_profile` to `project_storage` — a runtime column, never a YAML map (ADR-012). |
| **A-8** | 7 days is an acceptable delete grace period | Compliance may require shorter (GDPR erasure) or longer (retention policy). **Still unverified** (OD-5). |
| **A-9** | ~~Tenants tolerate a three-call protocol~~ | **Resolved:** direct upload is off by default; the proxied path serves every current use case. |
| **A-10** | No regulatory requirement for data residency | Would force multi-region and per-tenant bucket routing — a significant redesign. |

**A-12 — Batch upload is idempotent per file, NOT atomic per batch.**

`POST /media/upload/batch` derives per-file idempotency keys from the batch key
plus the file index (`K:0`, `K:1`, …). Replaying a batch with the same key
therefore replays the files that completed and re-runs the ones that did not,
creating no duplicate records.

What it does **not** give you is batch-level atomicity. A batch that succeeded on
seven of twenty files stays that way; there is no rollback of the seven, and the
client sees the same partial result on replay. This is accepted deliberately
rather than closed with a batch-level idempotency record, because such a record
would be a second idempotency mechanism with its own leak modes and its own
reconciliation — exactly the duplication the per-file design avoids.

Clients must treat `results` as the source of truth and reconcile per file using
`originalFilename`. A client that treats a 207 as all-or-nothing will be wrong.

**A-13 — Two files in one batch may share a filename.** The per-file
idempotency key uses the file's *index*, not its name, for this reason. Clients
matching results back to their own tasks by `originalFilename` alone must handle
duplicate names themselves — the results array is in request order, so position
is the unambiguous join.

---

## 2. Risks

### Critical

| ID | Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|---|
| **R-1** | The leaked AWS key is exploited before rotation | Full bucket access — read, delete, or ransom every tenant's files | **Occurring now** | Phase 0.1, day one. CloudTrail audit for prior use. |
| **R-2** | Tenant-isolation bypass in the predecessor exploited before this ships | Cross-tenant read and write | High while the port is mesh-reachable | Phase 0 network restriction as an interim; API key auth in Phase 1 |
| **R-3** | A calling service is compromised and asserts another tenant's id | Cross-tenant read/write | Low while callers are internal | `fixed-org-id` pinning; audit attribution by client id; key rotation. **Not fully mitigable under this model** — see ADR-010. |

### High

| ID | Risk | Impact | Mitigation |
|---|---|---|---|
| **R-4** | Quota provisioning is an out-of-band call somebody must remember | A new project's first upload fails with a confusing error; looks like a service outage to the tenant | Clear `QUOTA_NOT_PROVISIONED` error distinct from `QUOTA_EXCEEDED`; resolve OD-3 so provisioning is automatic on project creation |
| **R-5** | Pagination change breaks an unknown consumer | Broken listing in production | Dual support through Phase 3; per-consumer usage metrics before removal |
| **R-6** | Purging git history requires a coordinated force-push | Lost work if anyone has unpushed commits | Scheduled window; every developer re-clones; announced 48 h ahead |
| **R-7** | Migrating off `MediaUploadOrchestrator` breaks reads | The class is half-live — controllers use it for all read paths while a different service handles writes | Migrate read methods **first**, then delete the upload path. Explicit in Phase 2.18. |
| **R-8** | Direct upload is abandoned at scale, holding quota | Tenants blocked by phantom consumption | 30-minute TTL; sweeper every 5 min; `storage.session.expired` alerting |

### Medium

| ID | Risk | Impact | Mitigation |
|---|---|---|---|
| **R-9** | Content inspection rejects files that previously succeeded | Existing workflows break on rollout | Log-only mode for two weeks; measure the rejection rate before enforcing |
| **R-10** | Outbox lag delays physical deletion | Storage cost, and a delayed compliance erasure | Lag alert at 5 min; permanent delete bypasses the grace period |
| **R-11** | Reconciliation and live traffic contend | Slow uploads during the nightly window | Bounded batches; off-peak schedule; advisory lock |
| **R-12** | Redis becomes a de-facto dependency through scope creep | An outage becomes an outage rather than a degradation | ADR-007 and ADR-008 confine Redis to caching. Enforce in review. |
| **R-13** | Keyset cursors leak ordering information | Minor information disclosure | Opaque, signed cursors; never raw ids |
| **R-14** | The three-call upload protocol is misimplemented by clients | Orphaned sessions, confused error handling | Publish a reference client alongside the OpenAPI spec |

### Low, accepted

| ID | Risk | Why accepted |
|---|---|---|
| **R-15** | Auto-increment ids remain enumerable if OD-2 defaults | Tenant-scoped lookups mean enumeration yields 404s, not data. Only volume leaks. |
| **R-16** | 507 for quota is unconventional | Preserved for downstream compatibility; changing it would break a consumer for a cosmetic gain |
| **R-17** | Outbox polling adds ≤1 s latency | No consumer is latency-sensitive |
| **R-18** | No dedup means higher storage cost | ADR-011: dedup is a cross-tenant information leak |

## 3. Things that will bite if ignored

Not risks with mitigations — **certainties** if the corresponding discipline lapses.

1. **An API key in a git commit, a log line, or a Slack message.** It is a bearer
   credential: whoever holds it is that service. This is the single most likely
   failure of the chosen auth model.
2. **The second credential leak.** Two have already happened. Without the
   `gitleaks` CI gate there will be a third, regardless of how careful anyone is.
3. **The third upload implementation.** Somebody will add a "special case" upload
   path. The single-port design and ADR-002 exist to make that visible in review.
4. **The reintroduced tenant-blind finder.** Someone will add `findById(Long)` to a
   repository because it is convenient. Only the ArchUnit rule stops it.
5. **Documentation drift.** If `15-implementation-status.md` is not updated at the
   end of every item, this whole documentation set becomes decorative within a
   month — exactly what happened to the current service's `ARCHITECTURE.md`.
6. **The `@Scheduled` method with no lock.** The next scheduled job someone adds
   will run on every replica unless the lock is part of the template.
