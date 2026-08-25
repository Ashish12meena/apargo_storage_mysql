package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.application.port.in.command.CompleteUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.InitiateUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.ProxiedUploadCommand;
import com.aigreentick.services.storage.application.port.in.UploadMediaUseCase;
import com.aigreentick.services.storage.application.port.out.AuditPort;
import com.aigreentick.services.storage.application.port.out.ClockPort;
import com.aigreentick.services.storage.application.port.out.ContentInspectorPort;
import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.application.port.out.OutboxPort;
import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.application.port.out.UploadSessionPort;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.application.port.in.result.UploadTicket;
import com.aigreentick.services.storage.config.properties.QuotaProperties;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import com.aigreentick.services.storage.domain.event.DomainEvent;
import com.aigreentick.services.storage.domain.exception.InvalidMediaException;
import com.aigreentick.services.storage.domain.exception.UnsupportedStorageOperationException;
import com.aigreentick.services.storage.domain.exception.UploadSessionExpiredException;
import com.aigreentick.services.storage.domain.exception.UploadSessionNotFoundException;
import com.aigreentick.services.storage.domain.media.Checksum;
import com.aigreentick.services.storage.domain.media.ContentType;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.media.MediaStatus;
import com.aigreentick.services.storage.domain.media.MediaType;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;
import com.aigreentick.services.storage.domain.upload.UploadMode;
import com.aigreentick.services.storage.domain.upload.UploadSession;
import com.aigreentick.services.storage.domain.upload.UploadSessionId;
import com.aigreentick.services.storage.domain.upload.UploadSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * The single upload implementation (ADR-002), in two modes.
 *
 * <p>Ordering is the whole design: DB row (PENDING) → object → DB row (ACTIVE).
 * A stored object therefore never exists without a row. The inverse — a row with
 * no object — is detectable and sweepable, so the asymmetry is deliberate
 * (docs/05 §7).
 *
 * <p>No storage call happens inside a transaction that can still roll back.
 */
@Service
@Slf4j
public class MediaUploadService implements UploadMediaUseCase {

    private final StoragePort storage;
    private final MediaRepositoryPort mediaRepository;
    private final UploadSessionPort sessionRepository;
    private final QuotaApplicationService quotaService;
    private final MediaValidationService validation;
    private final ContentInspectorPort inspector;
    private final OutboxPort outbox;
    private final AuditPort audit;
    private final ClockPort clock;
    private final QuotaProperties quotaProperties;
    private final StorageProperties storageProperties;
    private final MediaViewMapper viewMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final TransactionTemplate transactionTemplate;

    public MediaUploadService(StoragePort storage, MediaRepositoryPort mediaRepository,
                              UploadSessionPort sessionRepository, QuotaApplicationService quotaService,
                              MediaValidationService validation, ContentInspectorPort inspector,
                              OutboxPort outbox, AuditPort audit, ClockPort clock,
                              QuotaProperties quotaProperties, StorageProperties storageProperties,
                              MediaViewMapper viewMapper, IdempotencyGuard idempotencyGuard,
                              TransactionTemplate transactionTemplate) {
        this.storage = storage;
        this.mediaRepository = mediaRepository;
        this.sessionRepository = sessionRepository;
        this.quotaService = quotaService;
        this.validation = validation;
        this.inspector = inspector;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.quotaProperties = quotaProperties;
        this.storageProperties = storageProperties;
        this.viewMapper = viewMapper;
        this.idempotencyGuard = idempotencyGuard;
        this.transactionTemplate = transactionTemplate;
    }

    // ────────────────────────────── PROXIED ──────────────────────────────

    @Override
    public MediaView uploadProxied(ProxiedUploadCommand command) {
        String hash = idempotencyGuard.hashUpload(command.tenant(), command.originalFilename(),
                command.declaredContentType(), command.size());
        return idempotencyGuard.execute(command.tenant(), command.idempotencyKey(), hash,
                storedMediaId -> replayMedia(storedMediaId, command.tenant()),
                () -> {
                    MediaView view = doUploadProxied(command);
                    return new IdempotencyGuard.Recorded<>(view.id(), view);
                });
    }

    /** Resolves a previously-stored media id back into a response. */
    private Optional<MediaView> replayMedia(String mediaId, TenantRef tenant) {
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }
        try {
            return mediaRepository
                    .findByIdForTenant(MediaId.parse(mediaId), tenant)
                    .map(viewMapper::toView);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private MediaView doUploadProxied(ProxiedUploadCommand command) {
        // 1. Cheap pre-flight: filename, declared type, declared size. Rejects the
        // obvious cases without reading a byte or touching the database.
        //
        // No separate proxied ceiling: the tenant's profile limit is the only one.
        // At this service's target file sizes the proxied path handles everything,
        // and a second threshold would just be another number to keep in sync.
        validation.validateBeforeUpload(
                command.originalFilename(), command.declaredContentType(), command.size());

        // 2. AUTHORITATIVE type check, before any quota is reserved or row written.
        //
        // This used to run after the reservation, and that ordering was expensive
        // for exactly the caller this service exists to serve. A client that builds
        // a multipart part from a file resource sends application/octet-stream,
        // which MediaType.fromMimeType classifies as DOCUMENT — so an image was
        // pre-checked against the 16 MB document ceiling rather than its own 8 MB
        // one, reserved quota, streamed every byte to the storage backend, and was
        // only then measured against the real limit and refused. The refusal was
        // correct; everything it cost first was waste, and it left a reservation to
        // be unwound by the compensating path.
        //
        // Inspection reads a bounded header and has no side effects, so moving it
        // ahead of the reservation changes no outcome — the same files are accepted
        // and the same files are rejected. What changes is that a rejection now
        // costs one read of an already-buffered part instead of a quota
        // reservation, a PENDING row, and a full upload to storage.
        //
        // The check itself is NOT loosened, and the write ordering that the design
        // rests on is untouched: the PENDING row is still committed before any byte
        // reaches the backend, so a stored object still never exists without a row.
        Inspected inspected = inspect(command);

        // The key carries the DETECTED media type, not the declared one, so an
        // octet-stream image no longer lands under the document prefix.
        StorageKey key = StorageKey.generate(command.tenant(), inspected.mediaType(),
                validation.extensionOf(command.originalFilename()));

        // 3. Reserve quota + PENDING row, atomically. Before any byte is written.
        Reservation reservation = transactionTemplate.execute(status ->
                reserveAndCreatePending(command.tenant(), command.actor(), key, command.originalFilename(),
                        command.size(), command.declaredContentType(), inspected.mediaType(),
                        UploadMode.PROXIED, command.idempotencyKey()));
        if (reservation == null) {
            throw new IllegalStateException("reservation transaction returned no result");
        }

        // 4. Store. Outside any transaction.
        try {
            StoredResult stored = store(command, key, inspected, reservation.media());
            // 5. Activate.
            return transactionTemplate.execute(status ->
                    activate(reservation, stored, command.tenant(), command.actor()));
        } catch (RuntimeException e) {
            compensate(reservation, command.tenant());
            throw e;
        }
    }

    /**
     * Reads a bounded header, detects the real content type, and validates it.
     *
     * <p>Opens the content separately from {@link #store}. {@code ProxiedUploadCommand}
     * supplies content as a re-openable {@code Supplier<InputStream>} precisely so
     * this is possible; for a multipart part the bytes are already buffered in
     * memory or spooled by the container, so the second open is not a second
     * network read.
     */
    private Inspected inspect(ProxiedUploadCommand command) {
        try (InputStream raw = command.content().get()) {
            byte[] header = raw.readNBytes(validation.inspectionHeaderBytes());
            ContentType detected = inspector.inspect(header, command.declaredContentType(),
                    command.originalFilename());
            MediaType mediaType = validation.validateDetected(detected, command.size());
            return new Inspected(detected, mediaType);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "failed reading upload header for " + command.originalFilename(), e);
        }
    }

    private StoredResult store(ProxiedUploadCommand command, StorageKey key, Inspected inspected,
                               Media media) {
        try (InputStream stream = command.content().get()) {
            StoragePort.StoredObject object = storage.put(stream, new StoragePort.PutRequest(
                    key, command.size(), inspected.contentType().detected(), inspected.mediaType(),
                    command.tenant().orgId(), command.tenant().projectId()));
            return new StoredResult(object, inspected.contentType());
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading upload stream for " + media.originalFilename(), e);
        }
    }

    // ───────────────────────────── PRESIGNED ─────────────────────────────

    @Override
    public UploadTicket initiate(InitiateUploadCommand command) {
        if (!storageProperties.presignedUploadEnabled()) {
            throw new UnsupportedStorageOperationException("direct upload is disabled "
                    + "(storage.presigned-upload.enabled=false); use POST /api/v1/media/upload");
        }
        if (!storage.supportsPresignedUpload()) {
            throw new UnsupportedStorageOperationException(
                    "provider " + storage.providerType() + " cannot presign uploads");
        }
        MediaType declaredType = validation.validateBeforeUpload(
                command.originalFilename(), command.declaredContentType(), command.declaredSize());

        StorageKey key = StorageKey.generate(command.tenant(), declaredType,
                validation.extensionOf(command.originalFilename()));

        boolean multipart = command.declaredSize().value() > storageProperties.s3().multipartThresholdBytes();
        UploadMode mode = multipart ? UploadMode.PRESIGNED_MULTIPART : UploadMode.PRESIGNED_SINGLE;

        Reservation reservation = transactionTemplate.execute(status ->
                reserveAndCreatePending(command.tenant(), command.actor(), key, command.originalFilename(),
                        command.declaredSize(), command.declaredContentType(), declaredType, mode,
                        command.idempotencyKey()));
        if (reservation == null) {
            throw new IllegalStateException("reservation transaction returned no result");
        }

        try {
            Duration ttl = Duration.ofMinutes(storageProperties.s3().presignExpiryMinutes());
            StoragePort.PresignRequest request = new StoragePort.PresignRequest(
                    key, command.declaredSize(), ContentType.normalise(command.declaredContentType()), ttl);

            StoragePort.PresignedUpload presigned = multipart
                    ? storage.presignMultipart(request, partCount(command.declaredSize()))
                    : storage.presignPut(request);

            UploadSession updated = reservation.session();
            updated.attachProviderUploadId(presigned.providerUploadId());
            transactionTemplate.executeWithoutResult(status -> sessionRepository.save(updated));

            audit.record(command.tenant(), command.actor(), AuditPort.AuditAction.UPLOAD_INITIATED,
                    reservation.session().id().value(), null);

            return new UploadTicket(reservation.session().id().value(),
                    reservation.media().id().asString(), mode.name(), presigned.urls(),
                    presigned.requiredHeaders(), presigned.partSizeBytes(), presigned.expiresAt());
        } catch (RuntimeException e) {
            compensate(reservation, command.tenant());
            throw e;
        }
    }

    private int partCount(ByteSize size) {
        long partSize = storageProperties.s3().partSizeBytes();
        return (int) Math.max(1, (size.value() + partSize - 1) / partSize);
    }

    @Override
    public MediaView complete(CompleteUploadCommand command) {
        UploadSession session = sessionRepository
                .findByIdForTenant(UploadSessionId.of(command.uploadSessionId()), command.tenant())
                .orElseThrow(() -> new UploadSessionNotFoundException(
                        "no session " + command.uploadSessionId() + " for " + command.tenant()));

        // Idempotent: a repeat commit returns the same result and charges nothing.
        if (session.status() == UploadSessionStatus.COMMITTED) {
            return mediaRepository.findByIdForTenant(session.mediaId(), command.tenant())
                    .map(viewMapper::toView)
                    .orElseThrow(() -> new IllegalStateException(
                            "committed session " + session.id().value() + " has no media row"));
        }
        if (session.isTerminal()) {
            throw new UploadSessionExpiredException("session " + session.id().value()
                    + " is " + session.status());
        }
        if (session.isExpiredAt(clock.now())) {
            throw new UploadSessionExpiredException("session " + session.id().value() + " past TTL");
        }

        Media media = mediaRepository.findByIdForTenant(session.mediaId(), command.tenant())
                .orElseThrow(() -> new IllegalStateException(
                        "session " + session.id().value() + " has no media row"));

        try {
            if (session.mode() == UploadMode.PRESIGNED_MULTIPART) {
                storage.completeMultipart(session.storageKey(), session.providerUploadId(),
                        command.parts().stream()
                                .map(p -> new StoragePort.PartRef(p.partNumber(), p.etag()))
                                .toList());
            }

            StoragePort.StoredObject object = storage.head(session.storageKey())
                    .orElseThrow(() -> new InvalidMediaException(
                            "no object at " + session.storageKey().value() + "; upload was not completed"));

            if (object.size().value() != session.declaredSize().value()) {
                throw new InvalidMediaException("declared size " + session.declaredSize().value()
                        + " does not match stored size " + object.size().value());
            }

            byte[] header = storage.readRange(session.storageKey(), 0, validation.inspectionHeaderBytes());
            ContentType detected = inspector.inspect(header, media.contentType().declared(),
                    media.originalFilename());
            validation.validateDetected(detected, object.size());

            StoredResult stored = new StoredResult(object, detected);
            Reservation reservation = new Reservation(media, session);
            return transactionTemplate.execute(status ->
                    activate(reservation, stored, command.tenant(), command.actor()));

        } catch (RuntimeException e) {
            // Validation failed after the bytes landed: remove them and refund.
            log.warn("upload completion failed for session {}: {}", session.id().value(), e.toString());
            compensate(new Reservation(media, session), command.tenant());
            throw e;
        }
    }

    @Override
    public void abort(String uploadSessionId, TenantRef tenant) {
        Optional<UploadSession> found = sessionRepository
                .findByIdForTenant(UploadSessionId.of(uploadSessionId), tenant);
        if (found.isEmpty() || found.get().isTerminal()) {
            return; // Idempotent.
        }
        UploadSession session = found.get();
        Media media = mediaRepository.findByIdForTenant(session.mediaId(), tenant).orElse(null);
        compensate(new Reservation(media, session), tenant);
    }

    // ────────────────────────────── SHARED ──────────────────────────────

    protected Reservation reserveAndCreatePending(TenantRef tenant,
                                                  Actor actor,
                                                  StorageKey key, String filename, ByteSize size,
                                                  String declaredContentType, MediaType mediaType,
                                                  UploadMode mode, String idempotencyKey) {
        Instant now = clock.now();
        quotaService.reserveOrThrow(tenant, size);

        UploadSession session = UploadSession.open(tenant, key, mode, size, now,
                now.plus(quotaProperties.uploadSessionTtl()), idempotencyKey);

        Media media = Media.pending(tenant, key, filename, size,
                ContentType.of(declaredContentType, declaredContentType), mediaType,
                session.id().value(), actor, now);

        Media saved = mediaRepository.save(media);
        session.attachMedia(saved.id());
        UploadSession savedSession = sessionRepository.save(session);
        return new Reservation(saved, savedSession);
    }

    protected MediaView activate(Reservation reservation, StoredResult stored, TenantRef tenant,
                                 Actor actor) {
        Instant now = clock.now();
        Media media = reservation.media();
        media.confirm(stored.object().size(), Checksum.ofNullable(stored.object().checksumSha256()),
                stored.detected(), now);
        Media saved = mediaRepository.save(media);

        UploadSession session = reservation.session();
        session.commit(now);
        sessionRepository.save(session);

        outbox.append(new DomainEvent.MediaCreated(saved.id().asString(), tenant.orgId(), tenant.projectId(),
                saved.storageKey().value(), saved.size().value(), saved.mediaType().name(),
                saved.contentType().detected(), now));

        audit.record(tenant, actor, AuditPort.AuditAction.UPLOAD_COMPLETED, saved.id().asString(), null);
        quotaService.emitThresholdEventsIfCrossed(tenant);
        return viewMapper.toView(saved);
    }

    /**
     * Releases quota and removes any partial object. Best-effort by design: if this
     * fails, the session stays RESERVED and the sweeper reclaims it. Recovery never
     * depends on a catch block running, because a catch block does not run when the
     * process dies — which is precisely when recovery is needed.
     */
    private void compensate(Reservation reservation, TenantRef tenant) {
        UploadSession session = reservation.session();
        try {
            if (session.mode() == UploadMode.PRESIGNED_MULTIPART && session.providerUploadId() != null) {
                storage.abortMultipart(session.storageKey(), session.providerUploadId());
            }
            storage.delete(session.storageKey());
        } catch (RuntimeException e) {
            log.warn("compensating delete failed for {}; sweeper will reclaim. cause={}",
                    session.id().value(), e.toString());
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                quotaService.release(tenant, session.declaredSize());
                session.abort(clock.now());
                sessionRepository.save(session);
                Media media = reservation.media();
                if (media != null && media.status()
                        == MediaStatus.PENDING) {
                    media.expire(clock.now());
                    mediaRepository.save(media);
                }
            });
        } catch (RuntimeException e) {
            log.error("compensating quota release failed for {}; sweeper will reclaim",
                    session.id().value(), e);
        }
    }

    /** Internal carrier for a reservation in progress. */
    protected record Reservation(Media media, UploadSession session) {
    }

    /** Internal carrier for a completed storage write. */
    protected record StoredResult(StoragePort.StoredObject object, ContentType detected) {
    }

    /** Internal carrier for a validated content inspection. */
    protected record Inspected(ContentType contentType, MediaType mediaType) {
    }
}
