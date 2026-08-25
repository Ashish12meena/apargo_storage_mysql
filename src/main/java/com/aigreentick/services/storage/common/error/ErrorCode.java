package com.aigreentick.services.storage.common.error;

/**
 * Stable, machine-readable error identifiers. PART OF THE PUBLIC API CONTRACT:
 * append-only, never renamed, never repurposed within a major version.
 *
 * <p>The default message is what reaches the caller. It must never contain a
 * bucket name, filesystem path, storage key, SQL fragment, or upstream body.
 */
public enum ErrorCode {

    MEDIA_INVALID("The uploaded file is not valid."),
    MEDIA_TOO_LARGE("The file exceeds the maximum allowed size."),
    CONTENT_TYPE_NOT_ALLOWED("This file type is not permitted."),
    CONTENT_TYPE_MISMATCH("The file contents do not match the declared type."),
    REQUEST_INVALID("The request is malformed."),
    IDEMPOTENCY_KEY_REUSED("This idempotency key was used for a different request."),
    IDEMPOTENCY_KEY_REQUIRED("An Idempotency-Key header is required for this operation."),

    UNAUTHENTICATED("Authentication is required."),
    ACCESS_DENIED("You do not have permission to perform this action."),
    MEDIA_NOT_FOUND("Media not found."),

    MEDIA_ILLEGAL_STATE("The media item is not in a state that allows this operation."),
    REQUEST_IN_PROGRESS("An identical request is currently being processed."),
    UPLOAD_SESSION_EXPIRED("The upload session has expired."),
    UPLOAD_SESSION_NOT_FOUND("Upload session not found."),

    QUOTA_EXCEEDED("Storage quota exceeded."),
    QUOTA_NOT_PROVISIONED("Storage quota has not been provisioned for this project."),
    QUOTA_LIMIT_INVALID("The requested storage limit is not valid for this organisation."),

    RATE_LIMITED("Too many requests."),

    STORAGE_UNAVAILABLE("Storage backend is temporarily unavailable."),
    DEPENDENCY_UNAVAILABLE("A required downstream service is unavailable."),
    OPERATION_UNSUPPORTED("This operation is not supported by the active storage provider."),

    INTERNAL_ERROR("An unexpected error occurred."),

    // ── Batch upload ────────────────────────────────────────────────────────
    // APPENDED, never reordered: this enum is part of the public contract and
    // clients branch on the name.
    BATCH_FILES_REQUIRED("At least one file must be supplied."),
    BATCH_TOO_MANY_FILES("The batch contains more files than are permitted in one request."),
    BATCH_ITEM_SKIPPED("Not attempted: the storage backend was unavailable earlier in this batch.");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
