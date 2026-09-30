package com.apargo.services.storage.application.audit;

import com.apargo.platform.contract.audit.AuditEntityDto;
import com.apargo.platform.contract.audit.AuditEventDto;
import com.apargo.platform.contract.audit.AuditEventStatus;
import com.apargo.services.storage.application.audit.AuditConstants.EntityTypes;
import com.apargo.services.storage.application.audit.AuditConstants.Fields;
import com.apargo.services.storage.application.audit.AuditConstants.Metadata;
import com.apargo.services.storage.common.audit.AuditContext;
import com.apargo.services.storage.common.audit.AuditContextProvider;
import com.apargo.services.storage.config.properties.AuditProperties;
import com.apargo.services.storage.domain.media.Media;
import com.apargo.services.storage.domain.quota.Quota;
import com.apargo.services.storage.domain.quota.QuotaScope;
import com.apargo.services.storage.domain.shared.TenantRef;
import com.apargo.services.storage.domain.upload.UploadSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The ONE place audit events for the storage module are built.
 *
 * <p>Each public method builds one event and raises it as a Spring application
 * event; {@code AuditEventPublisher} sends it to Kafka after the surrounding
 * transaction commits (or at once, when there is none). Call it AFTER the write,
 * one line per action.
 *
 * <p><b>Never throws.</b> Every method catches and logs any failure to build or
 * raise the event: auditing must not change business logic, responses or
 * transactions. A method may also decide there is nothing to audit (a no-op
 * change) and raise nothing.
 *
 * <p>Deliberately NOT in events: original filenames (user-supplied, may carry
 * personal data; the service keeps them out of logs for the same reason),
 * storage keys (capability-bearing), presigned URLs, request bodies, stack traces.
 */
@Component
@Slf4j
public class StorageAuditEvents {

    private final ApplicationEventPublisher events;
    private final AuditContextProvider contextProvider;
    private final AuditProperties properties;

    public StorageAuditEvents(ApplicationEventPublisher events, AuditContextProvider contextProvider,
                              AuditProperties properties) {
        this.events = events;
        this.contextProvider = contextProvider;
        this.properties = properties;
    }

    // ───────────────────────────── MEDIA ─────────────────────────────

    /** A file became ACTIVE: proxied upload, or a presigned session completed. */
    public void mediaUploaded(Media media, UploadSession session) {
        raise(StorageAuditEventType.MEDIA_UPLOADED, context -> mediaEvent(context,
                StorageAuditEventType.MEDIA_UPLOADED, AuditEventStatus.SUCCESS,
                media.tenant(), media.id().asString())
                .changes(List.of())
                .metadata(Metadata.MEDIA_TYPE, enumName(media.mediaType()))
                .metadata(Metadata.CONTENT_TYPE, media.contentType() == null ? null : media.contentType().detected())
                .metadata(Metadata.SIZE_BYTES, media.billableSize().value())
                .metadata(Metadata.UPLOAD_MODE, session == null ? null : enumName(session.mode()))
                .metadata(Metadata.UPLOAD_SESSION_ID, session == null ? null : session.id().value())
                .build());
    }

    /**
     * Completing an accepted presigned upload failed after the bytes landed; the
     * session was aborted and, when {@code quotaReleased}, its quota refunded.
     */
    public void mediaUploadFailed(Media media, UploadSessionAuditSnapshot session, Throwable failure,
                                  boolean quotaReleased) {
        raise(StorageAuditEventType.MEDIA_UPLOADED, context -> mediaEvent(context,
                StorageAuditEventType.MEDIA_UPLOADED, AuditEventStatus.FAILURE,
                media.tenant(), media.id().asString())
                .changes(List.of())
                .metadata(Metadata.MEDIA_TYPE, enumName(media.mediaType()))
                .metadata(Metadata.DECLARED_SIZE_BYTES, session.declaredSizeBytes())
                .metadata(Metadata.UPLOAD_MODE, enumName(session.mode()))
                .metadata(Metadata.UPLOAD_SESSION_ID, session.sessionId())
                .metadata(Metadata.QUOTA_RELEASED, quotaReleased)
                .error(AuditErrorMapper.errorFrom(failure, context.traceId()))
                .build());
    }

    /** A file was soft-deleted. {@code changes} is {@code []} for a delete. */
    public void mediaDeleted(MediaAuditSnapshot before, boolean permanent) {
        raise(StorageAuditEventType.MEDIA_DELETED, context -> mediaEvent(context,
                StorageAuditEventType.MEDIA_DELETED, AuditEventStatus.SUCCESS,
                before.orgId(), before.projectId(), before.mediaId())
                .changes(List.of())
                .metadata(Metadata.MEDIA_TYPE, enumName(before.mediaType()))
                .metadata(Metadata.SIZE_BYTES, before.sizeBytes())
                .metadata(Metadata.PERMANENT, permanent)
                .build());
    }

    /** A soft-deleted file was restored. */
    public void mediaRestored(MediaAuditSnapshot before, Media after) {
        raise(StorageAuditEventType.MEDIA_RESTORED, context -> {
            AuditEventDto.Builder builder = mediaEvent(context, StorageAuditEventType.MEDIA_RESTORED,
                    AuditEventStatus.SUCCESS, before.orgId(), before.projectId(), before.mediaId());
            change(builder, Fields.STATUS, enumName(before.status()), enumName(after.status()));
            return builder
                    .metadata(Metadata.SIZE_BYTES, before.sizeBytes())
                    .build();
        });
    }

    /** A deleted file's stored object was physically removed. */
    public void mediaPurged(MediaAuditSnapshot before) {
        raise(StorageAuditEventType.MEDIA_PURGED, context -> mediaEvent(context,
                StorageAuditEventType.MEDIA_PURGED, AuditEventStatus.SUCCESS,
                before.orgId(), before.projectId(), before.mediaId())
                .changes(List.of())
                .metadata(Metadata.PREVIOUS_STATUS, enumName(before.status()))
                .metadata(Metadata.SIZE_BYTES, before.sizeBytes())
                .build());
    }

    /** A scan verdict was recorded. Nothing is raised when nothing changed. */
    public void mediaScanned(MediaAuditSnapshot before, Media after) {
        raise(StorageAuditEventType.MEDIA_SCANNED, context -> {
            if (before.scanStatus() == after.scanStatus() && before.status() == after.status()) {
                return null;
            }
            AuditEventDto.Builder builder = mediaEvent(context, StorageAuditEventType.MEDIA_SCANNED,
                    AuditEventStatus.SUCCESS, before.orgId(), before.projectId(), before.mediaId());
            change(builder, Fields.SCAN_STATUS, enumName(before.scanStatus()), enumName(after.scanStatus()));
            change(builder, Fields.STATUS, enumName(before.status()), enumName(after.status()));
            return builder
                    .metadata(Metadata.SIZE_BYTES, before.sizeBytes())
                    .build();
        });
    }

    // ───────────────────────── UPLOAD SESSIONS ─────────────────────────

    /** A presigned upload session was opened and its quota reserved. */
    public void uploadSessionCreated(UploadSession session, Media media) {
        raise(StorageAuditEventType.UPLOAD_SESSION_CREATED, context -> entityEvent(context,
                StorageAuditEventType.UPLOAD_SESSION_CREATED, AuditEventStatus.SUCCESS,
                session.tenant().orgId(), session.tenant().projectId(),
                EntityTypes.UPLOAD_SESSION, session.id().value())
                .changes(List.of())
                .metadata(Metadata.MEDIA_ID, media == null || media.id() == null ? null : media.id().asString())
                .metadata(Metadata.MEDIA_TYPE, media == null ? null : enumName(media.mediaType()))
                .metadata(Metadata.UPLOAD_MODE, enumName(session.mode()))
                .metadata(Metadata.DECLARED_SIZE_BYTES, session.declaredSize().value())
                .build());
    }

    /** The caller aborted a session. Nothing is raised when its status did not change. */
    public void uploadSessionAborted(UploadSessionAuditSnapshot before, UploadSession after) {
        sessionClosed(StorageAuditEventType.UPLOAD_SESSION_ABORTED, before, after);
    }

    /** The sweeper reclaimed an abandoned session. Nothing is raised when its status did not change. */
    public void uploadSessionExpired(UploadSessionAuditSnapshot before, UploadSession after) {
        sessionClosed(StorageAuditEventType.UPLOAD_SESSION_EXPIRED, before, after);
    }

    private void sessionClosed(StorageAuditEventType type, UploadSessionAuditSnapshot before,
                               UploadSession after) {
        raise(type, context -> {
            if (before.status() == after.status()) {
                return null;
            }
            AuditEventDto.Builder builder = entityEvent(context, type, AuditEventStatus.SUCCESS,
                    before.orgId(), before.projectId(), EntityTypes.UPLOAD_SESSION, before.sessionId());
            change(builder, Fields.STATUS, enumName(before.status()), enumName(after.status()));
            return builder
                    .metadata(Metadata.MEDIA_ID, before.mediaId())
                    .metadata(Metadata.UPLOAD_MODE, enumName(before.mode()))
                    .metadata(Metadata.RECLAIMED_BYTES, before.declaredSizeBytes())
                    .build();
        });
    }

    // ───────────────────────────── QUOTA ─────────────────────────────

    /**
     * An org or project limit was provisioned. Raises QUOTA_CREATED when there was
     * none, QUOTA_UPDATED when the limit changed, and nothing when it did not.
     */
    public void quotaProvisioned(QuotaScope scope, long orgId, Long projectId, Optional<Quota> before,
                                 Quota after) {
        StorageAuditEventType type = before.isPresent()
                ? StorageAuditEventType.QUOTA_UPDATED
                : StorageAuditEventType.QUOTA_CREATED;
        raise(type, context -> {
            Long oldMax = before.map(q -> q.max().value()).orElse(null);
            long newMax = after.max().value();
            if (oldMax != null && oldMax == newMax) {
                return null;
            }
            boolean projectScope = scope == QuotaScope.PROJECT;
            AuditEventDto.Builder builder = entityEvent(context, type, AuditEventStatus.SUCCESS,
                    orgId, projectScope ? projectId : null,
                    projectScope ? EntityTypes.PROJECT_QUOTA : EntityTypes.ORG_QUOTA,
                    String.valueOf(projectScope ? projectId : orgId))
                    .metadata(Metadata.SCOPE, enumName(scope));
            if (oldMax == null) {
                builder.changes(List.of()).metadata(Metadata.MAX_BYTES, newMax);
            } else {
                change(builder, Fields.MAX_BYTES, oldMax, newMax);
            }
            return builder.build();
        });
    }

    /** Reconciliation corrected a project's recorded usage to the actual usage. */
    public void quotaReconciled(TenantRef tenant, long recordedBytes, long actualBytes) {
        raise(StorageAuditEventType.QUOTA_RECONCILED, context -> {
            if (recordedBytes == actualBytes) {
                return null;
            }
            AuditEventDto.Builder builder = entityEvent(context, StorageAuditEventType.QUOTA_RECONCILED,
                    AuditEventStatus.SUCCESS, tenant.orgId(), tenant.projectId(),
                    EntityTypes.PROJECT_QUOTA, String.valueOf(tenant.projectId()));
            change(builder, Fields.USED_BYTES, recordedBytes, actualBytes);
            return builder
                    .metadata(Metadata.DRIFT_BYTES, recordedBytes - actualBytes)
                    .build();
        });
    }

    // ──────────────────────────── TEARDOWN ────────────────────────────

    /** An org or project teardown was accepted; {@code jobId} is the id returned to the caller. */
    public void storageTeardownRequested(String jobId, long orgId, Long projectId, boolean permanent,
                                         long estimatedFiles) {
        raise(StorageAuditEventType.STORAGE_TEARDOWN_REQUESTED, context -> teardownEvent(context,
                StorageAuditEventType.STORAGE_TEARDOWN_REQUESTED, AuditEventStatus.SUCCESS,
                jobId, orgId, projectId)
                .metadata(Metadata.PERMANENT, permanent)
                .metadata(Metadata.ESTIMATED_FILES, estimatedFiles)
                .build());
    }

    /** A teardown finished: no live files remain for the tenant. */
    public void storageTeardownCompleted(String jobId, long orgId, Long projectId, long filesRemoved,
                                         int batches) {
        raise(StorageAuditEventType.STORAGE_TEARDOWN_COMPLETED, context -> teardownEvent(context,
                StorageAuditEventType.STORAGE_TEARDOWN_COMPLETED, AuditEventStatus.SUCCESS,
                jobId, orgId, projectId)
                .metadata(Metadata.FILES_REMOVED, filesRemoved)
                .metadata(Metadata.BATCHES, batches)
                .build());
    }

    /** A teardown was dead-lettered after its retries ran out. */
    public void storageTeardownFailed(String jobId, long orgId, Long projectId, long filesRemovedSoFar,
                                      int batchesDone, int attempts, Throwable failure) {
        raise(StorageAuditEventType.STORAGE_TEARDOWN_COMPLETED, context -> teardownEvent(context,
                StorageAuditEventType.STORAGE_TEARDOWN_COMPLETED, AuditEventStatus.FAILURE,
                jobId, orgId, projectId)
                .metadata(Metadata.FILES_REMOVED, filesRemovedSoFar)
                .metadata(Metadata.BATCHES, batchesDone)
                .metadata(Metadata.ATTEMPTS, attempts)
                .error(AuditErrorMapper.errorFrom(failure, context.traceId()))
                .build());
    }

    // ───────────────────────────── ORPHANS ─────────────────────────────

    /** Stored objects with no database record were removed for one tenant. Nothing is raised for zero. */
    public void storageOrphansReclaimed(TenantRef tenant, int reclaimedCount, String storageProvider) {
        raise(StorageAuditEventType.STORAGE_ORPHANS_RECLAIMED, context -> {
            if (reclaimedCount <= 0) {
                return null;
            }
            return base(context, StorageAuditEventType.STORAGE_ORPHANS_RECLAIMED, AuditEventStatus.SUCCESS)
                    .orgId(tenant.orgId())
                    .projectId(tenant.projectId())
                    .changes(List.of())
                    .metadata(Metadata.RECLAIMED_COUNT, reclaimedCount)
                    .metadata(Metadata.STORAGE_PROVIDER, storageProvider)
                    .build();
        });
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** Context, source, module, type and status: what every event carries. */
    private AuditEventDto.Builder base(AuditContext context, StorageAuditEventType type, AuditEventStatus status) {
        return AuditEventDto.builder()
                .sourceService(properties.sourceService())
                .environment(properties.environment())
                .module(AuditConstants.MODULE)
                .eventType(type.name())
                .status(status)
                .actor(context.actor())
                .channel(context.channel())
                .requestId(context.requestId())
                .traceId(context.traceId())
                .ip(context.ip())
                .userAgent(context.userAgent());
    }

    /** {@link #base} plus tenant and the one record the event is about. */
    private AuditEventDto.Builder entityEvent(AuditContext context, StorageAuditEventType type,
                                              AuditEventStatus status, long orgId, Long projectId,
                                              String entityType, String entityId) {
        return base(context, type, status)
                .orgId(orgId)
                .projectId(projectId)
                .entity(AuditEntityDto.of(entityType, entityId));
    }

    private AuditEventDto.Builder mediaEvent(AuditContext context, StorageAuditEventType type,
                                             AuditEventStatus status, TenantRef tenant, String mediaId) {
        return mediaEvent(context, type, status, tenant.orgId(), tenant.projectId(), mediaId);
    }

    private AuditEventDto.Builder mediaEvent(AuditContext context, StorageAuditEventType type,
                                             AuditEventStatus status, long orgId, long projectId,
                                             String mediaId) {
        return entityEvent(context, type, status, orgId, projectId, EntityTypes.MEDIA, mediaId);
    }

    private AuditEventDto.Builder teardownEvent(AuditContext context, StorageAuditEventType type,
                                                AuditEventStatus status, String jobId, long orgId,
                                                Long projectId) {
        return entityEvent(context, type, status, orgId, projectId, EntityTypes.STORAGE_TEARDOWN, jobId)
                .changes(List.of())
                .metadata(Metadata.SCOPE, enumName(projectId == null ? QuotaScope.ORG : QuotaScope.PROJECT));
    }

    /** Adds a change only when the value really changed. */
    private static void change(AuditEventDto.Builder builder, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            builder.change(field, oldValue, newValue);
        }
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * Builds and raises one event. Nothing escapes: a failure here is a bug in
     * this class, and it must never become a failure of the business action.
     *
     * @param build returns the event, or null when there is nothing to audit
     */
    private void raise(StorageAuditEventType type, Function<AuditContext, AuditEventDto> build) {
        try {
            AuditEventDto event = build.apply(contextProvider.current());
            if (event != null) {
                events.publishEvent(event);
            }
        } catch (RuntimeException ex) {
            log.error("Could not build or raise audit event {}; the business action is unaffected", type, ex);
        }
    }
}
