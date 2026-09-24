package com.aigreentick.services.storage.common.error;

/**
 * Stable, machine-readable result codes for the {@code code} field of the
 * response wrapper (company API Standard §6). PART OF THE PUBLIC CONTRACT.
 *
 * <p>Each code carries the HTTP status it is returned with, so the status and
 * the code can never disagree and the exception handler needs no status table:
 * a {@code DomainException} names its code and the response follows from it.
 *
 * <p>Two groups:
 * <ul>
 *   <li>the standard's shared vocabulary ({@code BAD_REQUEST},
 *       {@code VALIDATION_FAILED}, ...), which clients handle generically by
 *       HTTP status;</li>
 *   <li>{@code <RESOURCE>_<PROBLEM>} codes for cases a client may handle
 *       specifically.</li>
 * </ul>
 *
 * <p>The default message is what reaches the caller. It must never contain a
 * bucket name, filesystem path, storage key, SQL fragment, or upstream body.
 *
 * <p><b>Per-file batch results</b> ({@code results[].error.code}) use the same
 * names. template-service branches on {@code QUOTA_EXCEEDED},
 * {@code QUOTA_NOT_PROVISIONED}, {@code CONTENT_TYPE_NOT_ALLOWED},
 * {@code CONTENT_TYPE_MISMATCH}, {@code MEDIA_TOO_LARGE},
 * {@code BATCH_ITEM_SKIPPED} and {@code INTERNAL_ERROR}: never rename those.
 *
 * <p>Renamed when the standard was adopted (2026-09-24, see ADR-015):
 * {@code REQUEST_INVALID} → {@code BAD_REQUEST} / {@code VALIDATION_FAILED},
 * {@code ACCESS_DENIED} → {@code FORBIDDEN},
 * {@code REQUEST_IN_PROGRESS} → {@code IDEMPOTENCY_KEY_IN_PROGRESS},
 * {@code DEPENDENCY_UNAVAILABLE} → {@code DEPENDENCY_FAILURE}.
 */
public enum ErrorCode {

    // ── Standard vocabulary ────────────────────────────────────────────────
    BAD_REQUEST(400, "The request could not be read."),
    UNAUTHENTICATED(401, "Authentication is required."),
    FORBIDDEN(403, "You do not have permission to perform this action."),
    NOT_FOUND(404, "No endpoint exists for this path."),
    METHOD_NOT_ALLOWED(405, "This HTTP method is not supported for this endpoint."),
    UNSUPPORTED_MEDIA_TYPE(415, "This request Content-Type is not supported."),
    VALIDATION_FAILED(422, "Request has invalid fields."),
    RATE_LIMITED(429, "Too many requests."),
    INTERNAL_ERROR(500, "An unexpected error occurred."),
    DEPENDENCY_FAILURE(502, "A required downstream service is unavailable."),
    SERVICE_UNAVAILABLE(503, "The service is temporarily busy. Retry shortly."),

    // ── Media ──────────────────────────────────────────────────────────────
    MEDIA_INVALID(422, "The uploaded file is not valid."),
    MEDIA_TOO_LARGE(413, "The file exceeds the maximum allowed size."),
    /**
     * The FILE's type is not on the allowlist. 415 is kept because it is the
     * closest HTTP meaning and template-service already handles it per file.
     */
    CONTENT_TYPE_NOT_ALLOWED(415, "This file type is not permitted."),
    CONTENT_TYPE_MISMATCH(422, "The file contents do not match the declared type."),
    MEDIA_NOT_FOUND(404, "Media not found."),
    MEDIA_ILLEGAL_STATE(409, "The media item is not in a state that allows this operation."),

    // ── Idempotency ────────────────────────────────────────────────────────
    IDEMPOTENCY_KEY_REQUIRED(400, "An X-Idempotency-Key header is required for this operation."),
    IDEMPOTENCY_KEY_REUSED(409, "This idempotency key was used for a different request."),
    IDEMPOTENCY_KEY_IN_PROGRESS(409, "A request with this idempotency key is still being processed."),

    // ── Direct upload sessions ─────────────────────────────────────────────
    UPLOAD_SESSION_EXPIRED(409, "The upload session has expired."),
    UPLOAD_SESSION_NOT_FOUND(404, "Upload session not found."),

    // ── Quota ──────────────────────────────────────────────────────────────
    /**
     * 507 Insufficient Storage: the literal HTTP meaning, and preserved for
     * existing consumers. Clients must not retry until space is freed.
     */
    QUOTA_EXCEEDED(507, "Storage quota exceeded."),
    /** The request is valid but the tenant is not set up yet: a state clash. */
    QUOTA_NOT_PROVISIONED(409, "Storage quota has not been provisioned for this project."),
    QUOTA_LIMIT_INVALID(422, "The requested storage limit is not valid for this organisation."),

    // ── Storage backend ────────────────────────────────────────────────────
    STORAGE_UNAVAILABLE(502, "Storage backend is temporarily unavailable."),
    OPERATION_UNSUPPORTED(501, "This operation is not supported by the active storage provider."),

    // ── Batch upload ───────────────────────────────────────────────────────
    BATCH_FILES_REQUIRED(422, "At least one file must be supplied."),
    BATCH_TOO_MANY_FILES(413, "The batch contains more files than are permitted in one request."),
    /** Per-file result only; never a request-level status. */
    BATCH_ITEM_SKIPPED(503, "Not attempted: the storage backend was unavailable earlier in this batch.");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** Generic code for a bare HTTP status (the servlet {@code /error} fallback). */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> MEDIA_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> VALIDATION_FAILED;
            case 429 -> RATE_LIMITED;
            case 502 -> DEPENDENCY_FAILURE;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status >= 400 && status < 500 ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
