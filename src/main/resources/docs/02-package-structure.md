# 02 — Package Structure and Dependency Boundaries

## 1. Layout

```
com.aigreentick.services.storage
├── domain/                   ← no dependencies. Business model.
│   ├── media/                Media, StorageKey, MediaStatus, ContentType, Checksum
│   ├── quota/                Quota, QuotaReservation, QuotaScope
│   ├── upload/               UploadSession, UploadMode, UploadSessionStatus
│   ├── shared/               TenantRef, Actor, ByteSize
│   ├── event/                DomainEvent
│   └── exception/            DomainException + subtypes
│
├── common/                   ← no dependencies. Cross-cutting, framework-free.
│   ├── constants/            ApiPaths, HeaderNames
│   ├── context/              RequestContext (trace/request id — NOT tenant)
│   └── error/                ErrorCode
│
├── application/              ← depends on domain + common
│   ├── port/in/              use cases we offer
│   │   ├── command/          inbound inputs (8 *Command records)
│   │   └── result/           inbound outputs (MediaView, QuotaView,
│   │                         UploadTicket, BatchUploadView)
│   ├── port/out/             capabilities we require; payloads are records
│   │                         NESTED in each port interface
│   ├── shared/               types crossing BOTH ports: MediaListQuery,
│   │                         PageView, Cursor
│   └── service/              implementations
│
├── api/                      ← depends on application, domain, common
│   ├── common/dto/response/  ApiResponse, ErrorBody, PageResponse — used by
│   │                         every module AND by filters outside v1
│   ├── v1/                   /api/v1/** — caller acts AS a tenant
│   │   ├── media/            controllers + dto/request|response + mapper
│   │   └── quota/            controller + dto/response + mapper
│   ├── internal/             /internal/** — caller acts ON a tenant
│   │   ├── media/            teardown
│   │   └── quota/            provisioning + dto/request
│   ├── error/                exception → HTTP
│   └── security/             TenantPrincipal, Scope, filters, MediaAccessGuard
│
├── infrastructure/           ← depends on application, domain, common
│   ├── persistence/          JPA entities, repositories, mappers
│   ├── storage/s3|local/     StoragePort implementations
│   ├── inspection/           Tika, structural validation
│   ├── ratelimit/            Redis Bucket4j
│   ├── outbox/               dispatcher, handlers, reaper
│   ├── client/waba/          outbound clients (transitional — ADR-009)
│   └── lock/                 scheduler coordination
│
└── config/                   ← Spring wiring only, no logic
    ├── PropertiesConfig      registers the typed property records
    ├── SchedulingConfig      @EnableScheduling + a sized TaskScheduler
    ├── SecurityConfig        filter chain order
    ├── StorageConfig         selects the active StoragePort
    ├── StartupAssertions     fail-fast configuration checks
    └── properties/           validated @ConfigurationProperties records
```

### Why `api` splits on `v1` vs `internal`

Not "external vs service-to-service" — **every** caller of this API is a service.
The line is *whose data the caller is touching*:

| | `api/v1/**` | `api/internal/**` |
|---|---|---|
| Caller acts | **as** a tenant, on its own data | **on** a tenant, from outside |
| Tenancy from | authenticated context (headers → `TenantPrincipal`) | **path variables** `{orgId}/{projectId}` |
| Caller's own tenant | meaningful | meaningless — placeholder `TenantRef(1,1)` |
| Filter | `TenantContextFilter` | `InternalCallerFilter` |
| Self-serviceable | yes | no — quota limits, org teardown |

One-line test: **does the caller own the data it is touching?** Yes → `v1`.
No → `internal`. Batch upload writes into the caller's own tenant, so it is `v1`
despite being called by a service.

`api/internal` is NOT a security perimeter — same Tomcat, same port, same API-key
mechanism. It is a split between two incompatible tenancy models that would
otherwise have to coexist in one filter.

### Why `command`/`result` sit under `port/in` but `MediaListQuery` does not

Commands are exclusively inbound, so they belong to the inbound port. The former
`application/query/` package mixed two unrelated things — inputs
(`MediaListQuery`, `Cursor`) and read models (`MediaView`, `QuotaView`) — under a
name that described only the first.

Three types cross **both** ports: `QueryMediaUseCase.list(MediaListQuery)` takes
it inbound and `MediaRepositoryPort.search(MediaListQuery)` passes the same object
outbound, and `PageView` is the return type of both. Filing those under `port/in`
would assert a direction they do not have, so they live in `application/shared/`.

Note the remaining asymmetry, which is deliberate: **out**-port payloads are
records nested inside their interface (`StoragePort.PutRequest`,
`StoragePort.StoredObject`), while **in**-port payloads get folders. In-port
payloads carry substantial javadoc — `ProxiedUploadCommand` documents why content
is a re-openable supplier — and nesting several of those makes a port unreadable.

**The application class carries no `@Enable*` annotations.** Configuration lives
beside what it configures, so someone reading `infrastructure/scheduler/` can find
the scheduler's thread pool without knowing to look three packages away. A stack of
`@Enable*` on the entry point is invisible configuration.

Two specifics worth knowing:

- `@ConfigurationProperties` is a **binding marker, not a stereotype**, so
  `@ComponentScan` does not register these records. They are listed explicitly in
  `PropertiesConfig`; forgetting one fails at startup by name.
- `@EnableTransactionManagement` is deliberately **absent**. Boot's
  `TransactionAutoConfiguration` already applies it whenever a
  `PlatformTransactionManager` exists, which the JPA starter guarantees. A no-op
  annotation is worse than none — the next reader cannot tell whether it matters.

## 2. Dependency rules

| Rule | Enforced by |
|---|---|
| `domain` imports nothing from this service except `domain` and `common` | ArchUnit |
| `domain` and `common` import no Spring, JPA, Jackson, servlet, or AWS types | ArchUnit |
| `application` never imports `api` or `infrastructure` | ArchUnit |
| `api` never imports `infrastructure` | ArchUnit |
| `infrastructure` is imported by nobody | ArchUnit |
| Vendor types never appear in a `port.out` signature | Review + ArchUnit |
| No controller reads a tenant header | ArchUnit |
| Spring Data interfaces are not injected outside `persistence.repository` | ArchUnit |
| Every `@ConfigurationProperties` class lives in `config.properties` | ArchUnit (Phase 1) |
| `*ForMaintenance` repository methods are called only from `application.service` | ArchUnit |

These are **CI gates, not conventions**. A boundary maintained by review is a
boundary that erodes; the current service's two upload orchestrators are what that
erosion looks like.

## 3. Persistence: JPA everywhere, one entity per table

Every table has an entity, and every write goes through Spring Data JPA. There is
no raw `JdbcTemplate` in the codebase.

| Table | Entity | Repository |
|---|---|---|
| `org_storage` | `OrgStorageEntity` | `OrgStorageJpaRepository` |
| `project_storage` | `ProjectStorageEntity` | `ProjectStorageJpaRepository` |
| `media` | `MediaEntity` | `MediaJpaRepository` |
| `upload_session` | `UploadSessionEntity` | `UploadSessionJpaRepository` |
| `idempotency_record` | `IdempotencyRecordEntity` | `IdempotencyJpaRepository` |
| `outbox_event` | `OutboxEventEntity` | `OutboxJpaRepository` |
| `media_audit` | `MediaAuditEntity` | `MediaAuditJpaRepository` |
| `scheduler_lock` | `SchedulerLockEntity` | `SchedulerLockJpaRepository` |

### Where `@Modifying` is used, and the rule that comes with it

Some operations must be a single atomic statement rather than
read-modify-write — quota reservation above all, where the invariant
`used <= max` lives in the WHERE clause so the database enforces it:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE ProjectStorageEntity p SET p.usedBytes = p.usedBytes + :size " +
       "WHERE p.orgId = :o AND p.projectId = :p AND p.usedBytes + :size <= p.maxBytes")
int reserve(...);   // rows affected says whether we won
```

**`clearAutomatically = true, flushAutomatically = true` is mandatory on every
`@Modifying` query in this codebase.** Bulk updates bypass the persistence context,
so without them a previously-loaded entity keeps a stale value after the statement
runs. This is the sharp edge of bulk operations inside JPA and it fails silently.

`nativeQuery = true` is used only where JPQL has no equivalent:
`ON DUPLICATE KEY UPDATE` (quota upsert, lock acquire), `FOR UPDATE SKIP LOCKED`
(outbox claim), and `UPDATE ... LIMIT` (teardown batching).

### `version` is a plain column, not `@Version`

On the quota entities, `version` is mapped as an ordinary column. All mutations are
conditional bulk updates that increment it explicitly; Hibernate's automatic
version checking applies only to entity-managed writes and would silently do
nothing for those statements while implying that it did. One mechanism, visible in
the SQL.

### Schema is created by Flyway, never by Hibernate

`ddl-auto: validate` in every environment. `update` is never used: it is
additive-only and best-effort — it will add a column but never drop one, rename
one, change a type, backfill data, or create an index it was not asked for, and
what it generates varies by Hibernate version. The failure mode is silent drift
between environments.

Flyway applies migrations at startup, so no table is ever created by hand.
`db/storage-complete.sql` is a reference document, not a step anyone performs.

## 4. Why domain and JPA entities are separate

A JPA entity needs a no-arg constructor and mutable fields. Those are precisely the
properties that make it unable to enforce an invariant. Merging the two means
either the domain gains public setters — and the lifecycle state machine in
[05](05-domain-design.md) becomes advisory — or the schema is shaped by Hibernate's
requirements rather than by query patterns.

The cost is one mapper class per aggregate. The benefit is that `Media.confirm()`
can reject an illegal transition and no caller can route around it.

## 5. Naming conventions

| Kind | Pattern | Example |
|---|---|---|
| Inbound port | `<Verb><Noun>UseCase` | `UploadMediaUseCase` |
| Outbound port | `<Noun>Port` | `StoragePort` |
| Outbound adapter | `<Tech><Noun>Adapter` | `RedisRateLimiterAdapter` |
| Command | `<Verb><Noun>Command` | `DeleteMediaCommand` |
| Read model | `<Noun>View` | `MediaView` |
| JPA entity | `<Noun>Entity` | `MediaEntity` |
| Config | `<Area>Properties` | `StorageProperties` |

Adapters are named after the technology so a swap is visible in the diff.

## 6. Component disposition

| Existing component | Action | Rationale |
|---|---|---|
| Package layering | **KEEP** | Correct and enforced |
| `StoragePort` | **KEEP + EXTEND** | Add presign, head, readRange, deleteAll |
| Quota atomic `UPDATE` path | **KEEP** | Correct, lock-free; the best code in the repo |
| `QuotaReconciliationService` | **KEEP + EXTEND** | Add orphan detection, drift metric, advisory lock |
| `GlobalExceptionHandler` mapping | **KEEP** | Add traceId, stop echoing internal messages |
| `ApiResponse` envelope | **KEEP** | Additive fields only |
| `MediaValidator` | **MODIFY** | Add magic-byte inspection; one allowlist |
| `S3StorageAdapter` | **MODIFY** | Singleton presigner, CloudFront, SSE, no silent fallback |
| `LocalFileSystemStorage` | **MODIFY + FENCE** | Dev-only, startup failure in prod |
| `Media` / quota entities | **MODIFY** | Lifecycle, audit, checksum columns |
| `UserContextInterceptor` | **REPLACE** | Header trust → verified claims |
| `RateLimitInterceptor` | **REPLACE** | In-JVM → Redis |
| `MediaCommandService` deletes | **REPLACE** | No storage delete, no tenant scope, no quota release |
| `MediaUploadOrchestrator.uploadMedia` | **DELETE** | Dead path — migrate its read methods first |
| Commented-out code | **DELETE** | Git has the history |
| `db/storage.sql` | **DELETE** | Replaced by Flyway; contains `DROP SCHEMA` |
| `application-storage.yml` | **DELETE** | Duplicate config and the credential leak vector |
| `storage.allowed-mime-types` | **DELETE** | Vestigial second allowlist |
| Duplicate resilience config block | **DELETE** | Two blocks hand-synced by comment |
| `MediaType` per-type byte limits | **DELETE** | Third source of one rule; moves to config |
| Facebook / WABA integration | **MOVE** | Out of the hot path; ideally out of the service (ADR-009) |
