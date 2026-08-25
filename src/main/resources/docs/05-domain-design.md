# 05 — Domain Design and Business Rules

## 1. Ubiquitous language

| Term | Meaning |
|---|---|
| **Media** | One stored file and its metadata. The primary aggregate. |
| **Storage key** | Path of the object within a backend. Capability-bearing until an ownership check is applied. |
| **Tenant** | An (org, project) pair. The isolation boundary. |
| **Actor** | The principal performing an operation. Distinct from tenant: tenant is *where*, actor is *who*. |
| **Quota** | A byte allowance at org or project scope, plus its consumption. |
| **Reservation** | Quota charged before bytes are written, held under a TTL. |
| **Upload session** | A reservation bound to one prospective object. |
| **Purge** | Physical removal of the stored object, after soft delete. |
| **Drift** | Divergence between recorded `used_bytes` and the true sum. |
| **Orphan** | An object in storage with no database row. Currently undetectable. |

## 2. Aggregates

| Aggregate | Root | Consistency boundary |
|---|---|---|
| Media | `Media` | One row |
| Quota | `Quota` | One quota row at one scope |
| Upload session | `UploadSession` | One row |

**Media and Quota are deliberately separate aggregates** despite changing
together. Merging them would put every upload in a project behind one aggregate
lock, serialising the tenant. They are updated in the same *transaction*, which
gives atomicity, without being the same *aggregate*, which would give contention.

## 3. Invariants

### 3.1 Tenant
- `orgId > 0` and `projectId > 0`.
- Constructed only from verified credentials. There is no factory taking a header.
- Every persisted row and every query carries one.

### 3.2 Storage key
- Always `org-{orgId}/proj-{projectId}/{mediaType}/{uuid}[.ext]`.
- Always generated server-side. A client-supplied key is never used for a write.
- Never contains `..`, a leading `/`, a backslash, or a null byte.
- Globally unique — enforced by a unique index, not by trusting UUID collision odds.

### 3.3 Byte size
- Never negative. Releasing more than was reserved is a **bug to surface**, not a
  value to clamp. Clamping is how drift hides.

### 3.4 Media
- `sizeBytes > 0`.
- `originalFilename` non-blank, no path separators, ≤ 255 characters.
- `checksumSha256` present once `ACTIVE`.
- `detectedContentType` present once `ACTIVE`, and on the allowlist.
- An `ACTIVE` media item **always** has a corresponding stored object.

### 3.5 Quota
- `usedBytes >= 0` and `maxBytes >= 0` (database `CHECK` constraints).
- `usedBytes <= maxBytes` after any successful reservation. Enforced by the
  conditional `UPDATE`, not by application code.
- A project quota row requires its org quota row to exist (foreign key).
- Lowering `maxBytes` below `usedBytes` is **permitted**. The tenant simply cannot
  upload until they free space. Rejecting the change would leave billing and
  enforcement disagreeing, which is worse than a temporarily over-limit tenant.
- **A project limit may never exceed its organisation's total.** Enforced at
  provisioning time, so the number an administrator sees on a project is always a
  number that could actually be used. Violations raise `QUOTA_LIMIT_INVALID` (400),
  distinct from `QUOTA_EXCEEDED` (507): the remedy is an administrative correction,
  not deleting files.
- **Lowering an org limit below an existing project limit is rejected**, listing
  the offending value. Silently clamping projects would shrink allowances an
  administrator believes they granted, without telling anyone.
- **Project limits may sum above the org total by default**
  (`quota.allow-project-overcommit: true`). Each project is still individually
  capped at the org total, and the org row is still checked on every reservation,
  so the real bound holds regardless — this only decides whether a per-project
  number is a cap or a guarantee. Set false to make it a guarantee.

## 4. Media lifecycle

```
                    ┌──────────┐
   initiate ───────▶│ PENDING  │
                    └────┬─────┘
              confirm    │    TTL lapses
            ┌────────────┴────────────┐
            ▼                         ▼
       ┌─────────┐               ┌─────────┐
       │ ACTIVE  │               │ EXPIRED │ ──▶ row + object removed
       └────┬────┘               └─────────┘
     delete │      ▲ restore (within grace)
            ▼      │
       ┌─────────┐ │        scan: INFECTED
       │ DELETED │─┘   ┌──────────────────────┐
       └────┬────┘     │     QUARANTINED      │◀── from ACTIVE
            │ reaper   └──────────────────────┘
            ▼
       ┌─────────┐
       │ PURGED  │   terminal
       └─────────┘
```

| From | To | Trigger | Rule |
|---|---|---|---|
| — | PENDING | initiate / proxied start | Quota reserved first |
| PENDING | ACTIVE | confirm | Object verified present, size matches, content inspected |
| PENDING | EXPIRED | sweeper | Quota released, partial object deleted |
| ACTIVE | DELETED | delete | Quota released, `media.deleted` emitted |
| ACTIVE | QUARANTINED | scan result | Reads blocked; quota still charged |
| DELETED | ACTIVE | restore | Only while `purge_after` is in the future |
| DELETED | PURGED | reaper | Only after the object is confirmed gone |

**Forbidden**: ACTIVE → PENDING, PURGED → anything, EXPIRED → anything. These are
rejected by the aggregate, which is why `Media` is not a JPA entity with setters.

## 5. Upload session lifecycle

```
   RESERVED ──commit──▶ COMMITTED
      ├── abort ──────▶ ABORTED
      └── TTL ────────▶ EXPIRED
```

Quota is charged at `RESERVED` and released on `ABORTED` / `EXPIRED`. **Charging at
reservation rather than at commit is what makes concurrent uploads correct** — if
quota were charged at commit, N concurrent uploads could all pass a capacity check
and collectively overcommit.

## 6. Business rules

### 6.1 Quota reservation
1. Reservation is atomic across project **and** org. Partial reservation is
   impossible.
2. Lock order is always **project, then org**. Never reversed. This is what
   prevents deadlock between concurrent uploads to different projects of one org,
   and it is preserved verbatim from the current implementation.
3. Insufficient quota → `QuotaReservation.Exceeded` → `507`.
4. No quota row → `QuotaReservation.NotProvisioned` → `400`. Deliberately distinct:
   the remedy is admin provisioning, not deleting files.
5. Release floors at zero and never goes negative.
6. Release is safe to repeat. A concurrent duplicate delete observes 0 rows
   affected on the conditional status update and skips the release entirely.

### 6.2 Validation ordering

Cheapest and most likely to fail first, so an attacker cannot make the service do
expensive work before rejecting:

```
size → filename → declared type on allowlist → quota reserve
     → bytes written → magic-byte inspection → structural limits → ACTIVE
```

Content inspection deliberately comes **after** the bytes exist, because for direct
uploads there is no earlier opportunity — a presigned PUT cannot be inspected in
flight. It comes **before** `ACTIVE`, so an unvalidated object is never readable.

### 6.3 Idempotency
1. Same key + same request hash + completed → replay the stored response exactly.
2. Same key + **different** request hash → `422`. Silently returning the cached
   response for a different request would be worse than an error.
3. Same key + in progress → `409` with `Retry-After`.
4. Records are tenant-scoped, so keys cannot collide or be probed across orgs.
5. Records expire after 24 hours.

### 6.4 Deletion
0. **`deleted_at` is a fact; `purge_after` is a policy.** `deleted_at` records when
   the delete happened and never moves — it belongs to the audit record.
   `purge_after` records when removal is permitted: `now + grace` for a routine
   delete, `now` for a compliance erasure. Without this split, `?permanent=true`
   had nowhere to record its intent and the reaper applied the grace period to
   everything.
1. Soft delete releases quota **immediately**. A tenant should not wait out a grace
   period to regain space.
2. The stored object survives the grace period, so restore is possible.
3. Storage deletion **never** happens inside a transaction that can roll back. A
   rollback after the object is gone leaves a row pointing at nothing.
4. Deleting an absent object is a success. Retries must converge.
5. `PURGED` is set only after the object is confirmed gone.

### 6.5 Authorization
1. Scope is checked **before** any lookup, so a caller lacking the scope learns
   nothing about existence.
2. Every lookup is tenant-scoped in its repository signature. There is no
   tenant-blind finder to misuse.
3. Cross-tenant access returns `404`, never `403`.
4. Storage keys are verified against the caller's own prefix on every read path.

## 7. The consistency invariant

> **A stored object never exists without a database row. A row without an object is
> acceptable and repairable.**

The asymmetry is the whole design. An orphaned object is invisible to
reconciliation (which sums `media` rows), unreclaimable without a full bucket scan,
and billed indefinitely. A row with no object is detectable on read and repairable.

Therefore:

| Operation | Order |
|---|---|
| Create | row (`PENDING`) → object → row (`ACTIVE`) |
| Delete | row (`DELETED`) → outbox → object → row (`PURGED`) |

And: **never call storage inside a transaction that can still roll back.**

Failure points and their recovery are enumerated in
[11-production-readiness.md §5](11-production-readiness.md).
