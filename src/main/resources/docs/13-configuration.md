# 13 — Configuration Requirements

## 1. Rules

1. **No secret has a default value in any file.** A missing environment variable
   **fails startup**. The current `application-database.yml` ships a real DB
   password as a default and `application-storage.yml` ships a live AWS key pair.
2. **No security control has an enable/disable flag.** The current
   `internal.api.auth-enabled` defaults to `false` outside the prod profile, so an
   environment started with the wrong profile runs unauthenticated. A control that
   fails open on a missing variable is not a control.
3. **One concept, one property.** Upload limits currently have three sources — the
   `MediaType` enum, `media.service.upload-*`, and `storage.max-file-size` — that
   already disagree.
4. **Everything binds to a validated `@ConfigurationProperties` record**, so
   misconfiguration fails at startup rather than at the first request needing the
   value.
5. **Profiles compose by concern**, not by environment.

## 2. Profiles

| Profile | Purpose |
|---|---|
| *(base)* | Structure and non-sensitive defaults. No secrets, no credentials. |
| `local` | Developer machine. MinIO, dev JWT issuer, seed data. |
| `test` | Testcontainers. |
| `prod` | Narrowed actuator, JSON logging, strict startup assertions. |

**Deleted:** `application-storage.yml` — a duplicate of the storage tree in the base
file, not referenced by `spring.profiles.include`, and the credential-leak vector.

## 3. The `.env` files

`.env.example` is committed and lists all 46 variables with development defaults
and a comment on each. `.env` is gitignored and holds real values.

```bash
cp .env.example .env
```

`.gitignore` carries `!.env.example` so the template survives the `.env.*`
exclusion — without it, a new developer has no way to know which variables exist.

`docker-compose.yml` reads `.env` via `env_file`, then overrides the handful that
must point at container hostnames rather than localhost.

Outside containers, `application.yml` imports it directly:

```yaml
spring:
  config:
    import: optional:file:./.env[.properties]
```

Spring Boot 3 parses the file as a `.properties` document — which `KEY=value`
already is — so no library is needed. `optional:` means a missing `.env` is not
an error, so CI and Kubernetes start fine without one.

**Precedence matters here:** OS environment variables sit ABOVE imported config
data. `.env` is a development convenience that production silently overrides, so
a real `DB_PASSWORD` in the environment always beats anything on disk.

**One inherited caveat:** in a `.properties` file, `#` starts a comment only at
the beginning of a line. Written inline, it becomes part of the value —
`TRACE_SAMPLE_RATE=1.0   # 0.1 in prod` parses as the literal string
`"1.0   # 0.1 in prod"`. Every comment in `.env.example` is therefore on its own
line, and it must stay that way.

**Four variables have no default and fail startup if unset:** `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`. That is deliberate — a missing
credential must stop the boot, never fall back to something that happens to work.

## 4. Required environment variables

Every one of these is **required in production**. Absent → startup fails.

| Variable | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Must include `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Password from Secrets Manager |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | |
| `STORAGE_PROVIDER` | Must be `s3` in prod — **enforced at startup** |
| `S3_BUCKET`, `S3_REGION`, `S3_KMS_KEY_ID` | |
| `CLOUDFRONT_DOMAIN` | Absence means every read is billed at S3 egress |
| `TEMPLATE_SERVICE_API_KEY`, `CHAT_SERVICE_API_KEY`, `ORG_SERVICE_API_KEY` | ≥32 chars, from a secret store |
| `TRUSTED_PROXY_CIDRS` | Narrow to the load balancer subnet |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | |

**Deliberately absent:** `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. Removed
from the configuration schema entirely, so they cannot be set even deliberately.
S3 access uses IRSA.

## 5. Property reference

### `storage.*`

```yaml
storage:
  active-provider: ${STORAGE_PROVIDER}          # local | s3 | minio
  local:
    root-path: ${LOCAL_STORAGE_ROOT:./media-uploads}
    base-url:  ${LOCAL_STORAGE_BASE_URL:http://localhost:7998/api/v1/media/serve/}
  s3:
    bucket:            ${S3_BUCKET}
    region:            ${S3_REGION}
    endpoint:          ${S3_ENDPOINT:}           # MinIO only
    cloudfront-domain: ${CLOUDFRONT_DOMAIN:}
    kms-key-id:        ${S3_KMS_KEY_ID}
    storage-class:     ${S3_STORAGE_CLASS:INTELLIGENT_TIERING}
    multipart-threshold-bytes: 104857600         # 100 MB
    part-size-bytes:            16777216         # 16 MB
    presign-expiry-minutes:           15
```

### `media.validation.*` — global limits

Per-file limits are the same for every tenant. What varies per project is total
storage **capacity**, and that lives in `project_storage.max_bytes` in the
database, set at runtime through `/internal/quota/project` (ADR-012).

The dividing line: deployment configuration in YAML, tenant data in the database.
Onboarding a tenant must never require a deploy.

```yaml
media:
  validation:
    max-bytes: 16777216            # 16 MB — WhatsApp media ceiling
    inspection-header-bytes: 8192
    reject-on-type-mismatch: true

    max-bytes-by-media-type:       # optional; absent types fall back to max-bytes
      IMAGE: 8388608
      VIDEO: 16777216
      AUDIO: 16777216
      DOCUMENT: 16777216

    allowed-mime-types-by-media-type:
      IMAGE:    [image/jpeg, image/png, image/webp]
      VIDEO:    [video/mp4, video/3gpp]
      AUDIO:    [audio/mpeg, audio/mp4, audio/ogg, audio/aac, audio/amr]
      DOCUMENT: [application/pdf, text/plain, ...]
```

A per-type value **above** `max-bytes` is clamped down to it, so one careless
override cannot raise the global ceiling.

`image/jpg` is deliberately absent — it is not a real MIME type and is normalised
to `image/jpeg` during inspection, in one place.

**Startup fails** if the allowlist is empty: otherwise every upload returns 415
with no obvious cause.

`spring.servlet.multipart.max-file-size` must sit at or above `max-bytes`, or
Tomcat rejects the upload before the service can produce a useful error. The
effective values of both are logged at startup.

### `media.scanning.*` — off by default

```yaml
media:
  scanning:
    enabled: false
    provider: noop                      # noop | clamav
    endpoint: ""
    timeout: PT30S
    max-scan-bytes: 104857600
    block-download-until-scanned: false
```

The capability is always wired (ADR-014). Enabling it is this flag plus a scanner
adapter — no schema change, no lifecycle change, no API change. Scanning consumes
`media.created` from the outbox and is never in the request path.

**Startup fails if `enabled: true` while `provider: noop`**: scanning switched on
with nothing behind it is worse than off, because every file appears to have
passed.

`block-download-until-scanned` is a separate, stricter policy. It makes scan
latency visible to every reader, so it is off by default.

### `storage.presigned-upload.*` — off by default

```yaml
storage:
  presigned-upload:
    enabled: false
    threshold-bytes: 33554432          # 32 MB
```

At this service's file sizes the proxied path is a single round trip and the
three-call protocol earns nothing (ADR-004, revised). The machinery stays wired so
raising a limit for one tenant is a YAML flip rather than a re-architecture.

Note `spring.servlet.multipart.max-file-size` must sit at or above
`media.validation.max-bytes`, or Tomcat rejects an upload before the service can
produce a useful error.

### `quota.*`

```yaml
quota:
  upload-session-ttl:  PT30M
  delete-grace-period: P7D
  alert-threshold-percents: [80, 95]
  reconciliation-enabled: true
  reconciliation-cron: "0 0 3 * * *"
  # A project limit may never exceed the org total — always enforced, not a flag.
  # This controls the weaker rule: whether project limits may SUM above the org
  # total. True = per-project numbers are caps; false = they are guarantees.
  allow-project-overcommit: true
```

### `scheduling.*`

```yaml
scheduling:
  enabled: true
  pool-size: 4        # must exceed the number of concurrent long-running jobs
```

The default Spring scheduler is single-threaded, which would let a slow nightly
job block the 1-second outbox poll. See 11-production-readiness.md § Scheduling.

### `security.*`

```yaml
security:
  api-key-enabled: true          # false is dev-only; prod startup fails on false
  api-key-header: X-Api-Key

  clients:                       # one entry per calling service
    - id: template-service
      key: ${TEMPLATE_SERVICE_API_KEY}
      scopes: [media:read, media:write, media:delete]
    - id: org-service
      key: ${ORG_SERVICE_API_KEY}
      scopes: [quota:admin, quota:read]
      # fixed-org-id: 42         # pins this caller to one org; headers ignored

  trusted-proxy-cidrs: ${TRUSTED_PROXY_CIDRS:10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}
  cors-allowed-origins: ${CORS_ALLOWED_ORIGINS:}
```

Per-client keys mean rotation is one caller at a time — add the new key, deploy
that caller, remove the old one. No flag day.

`fixed-org-id` is the one control that fully closes the confused-deputy gap for a
caller: the tenant headers are ignored and that client can only ever act for its
own organisation. Startup logs a warning naming every client that is **not**
pinned.

`trusted-proxy-cidrs` is required: without it `X-Forwarded-For` is
attacker-controlled and any caller can spoof a fresh rate-limit bucket. The RFC
1918 defaults trust every pod in the VPC — narrow them to the load balancer's
subnet in production.

### `rate-limit.*`

```yaml
rate-limit:
  enabled: true
  fail-open: true          # availability over enforcement — see 09 §8
  rules:
    upload-per-user:    { capacity: 60,   refill-tokens: 60,   refill-period: PT1M }
    upload-per-project: { capacity: 300,  refill-tokens: 300,  refill-period: PT1M }
    read-per-project:   { capacity: 1000, refill-tokens: 1000, refill-period: PT1M }
    serve-per-project:  { capacity: 500,  refill-tokens: 500,  refill-period: PT1M }
    anonymous-per-ip:   { capacity: 20,   refill-tokens: 20,   refill-period: PT1M }
  bandwidth-bytes-per-minute:
    upload-per-org: 5368709120
```

`serve-per-project` is new. The current limiter recognises only `/upload` and
`/media/{id}`, so the serve path falls to a default IP bucket — effectively
unthrottled per tenant, on the one route that streams unbounded bytes off disk.

### Batch upload

| Property | Env | Default | Notes |
|---|---|---|---|
| `storage.max-files-per-batch` | `MAX_FILES_PER_BATCH` | `20` | Files accepted by one `POST /media/upload/batch`. Bounds how long one servlet thread is held, since the batch is sequential. |
| `spring.servlet.multipart.max-file-size` | `MULTIPART_MAX_FILE_SIZE` | `20MB` | **Unchanged by batch.** A batch changes nothing about what one file may be. |
| `spring.servlet.multipart.max-request-size` | `MULTIPART_MAX_REQUEST_SIZE` | `100MB` | Raised for batch. Tomcat rejects an oversized request before application code runs, so exceeding this returns a raw container error, not the service envelope. |

`20 x 20MB = 400MB`, far above the 100MB request cap. **This inconsistency is
deliberate.** The request cap protects the service; it is not a promise that
every permitted batch fits. A caller sending twenty maximum-size videos gets a
payload-too-large error and should split the batch. Do not "fix" it by raising
the cap.

**Rate limiting: batch upload is exempt.** `/media/upload/batch` is skipped by
`RateLimitFilter.shouldNotFilter`. It is reached only by an authenticated
internal caller and is already bounded by `storage.max-files-per-batch` (files
per request) and `max-request-size` (bytes per request); a token bucket would
duplicate those. A per-caller bucket was also unsound here — an API-key caller's
only identity is the client id, so every org and project sharing that client
would have shared one bucket, letting one tenant throttle all the others. The
tenant-facing single-file routes remain limited.

---

## 6. Startup assertions

Fail fast, loudly, at boot:

| Assertion | Failure |
|---|---|
| `prod` profile + `active-provider: local` | **Startup fails** |
| `media.validation.allowed-mime-types-by-media-type` empty | **Startup fails** |
| `media.scanning.enabled: true` with `provider: noop` | **Startup fails** |
| `security.api-key-enabled: false` under the `prod` profile | **Startup fails** |
| `security.clients` empty in production | **Startup fails** |
| An API key shorter than 32 characters | **Startup fails** |
| A client not pinned with `fixed-org-id` | **WARN** — it may assert any tenant |
| Any secret unset in prod | Startup fails |
| `reject-on-type-mismatch: false` in prod | Startup fails |
| Exactly one storage provider bean | Startup fails |
| Flyway at the expected version | Startup fails |
| CORS contains `*` in prod | Startup fails |
| `cloudfront-domain` empty in prod | **WARN** — works, but every read is billed at S3 egress |

## 7. Actuator

| Profile | Exposed |
|---|---|
| local / test | `health,info,metrics,prometheus,env,beans,loggers,configprops` |
| **prod** | `health,info,metrics,prometheus` **only** |

`env`, `beans`, `loggers`, and `configprops` leak bound configuration including
secrets. The current base configuration exposes `env`, `beans`, and `loggers` with
`show-details: always` in **every** environment.
