package com.apargo.services.storage.application.audit;

/**
 * Every audit event type this service produces: {@code <ENTITY>_<PAST_VERB>}.
 * Success or failure is the event's {@code status}, never part of the name.
 *
 * <p>Adding a value is safe. Renaming or removing one breaks consumers. The
 * catalogue — trigger, actor, entity, changes and metadata of each — is in
 * {@code docs/01-architecture.md} §8.
 */
public enum StorageAuditEventType {

    /** A file became ACTIVE (proxied upload, or a presigned session completed). FAILURE: completion failed after the bytes landed. */
    MEDIA_UPLOADED,

    /** A file was soft-deleted (single or batch delete). */
    MEDIA_DELETED,

    /** A soft-deleted file was restored within its grace period. */
    MEDIA_RESTORED,

    /** A deleted file's object was physically removed. */
    MEDIA_PURGED,

    /** A malware scan recorded its verdict (and quarantined an infected file). */
    MEDIA_SCANNED,

    /** A presigned (direct) upload session was opened and quota reserved. */
    UPLOAD_SESSION_CREATED,

    /** A presigned upload session was aborted by the caller; quota released. */
    UPLOAD_SESSION_ABORTED,

    /** An abandoned upload session was reclaimed by the sweeper; quota released. */
    UPLOAD_SESSION_EXPIRED,

    /** An org or project storage limit was provisioned for the first time. */
    QUOTA_CREATED,

    /** An existing org or project storage limit was changed. */
    QUOTA_UPDATED,

    /** Nightly reconciliation corrected a project's recorded usage. */
    QUOTA_RECONCILED,

    /** An org or project storage teardown was accepted. */
    STORAGE_TEARDOWN_REQUESTED,

    /** A storage teardown finished. FAILURE: it was dead-lettered. */
    STORAGE_TEARDOWN_COMPLETED,

    /** Stored objects with no database record were removed for a tenant. */
    STORAGE_ORPHANS_RECLAIMED
}
