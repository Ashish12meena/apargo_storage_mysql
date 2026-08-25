package com.aigreentick.services.storage.api.error;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.api.common.dto.response.ErrorBody;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.aigreentick.services.storage.domain.exception.BatchTooLargeException;
import com.aigreentick.services.storage.domain.exception.ContentTypeMismatchException;
import com.aigreentick.services.storage.domain.exception.ContentTypeNotAllowedException;
import com.aigreentick.services.storage.domain.exception.DomainException;
import com.aigreentick.services.storage.domain.exception.IdempotencyConflictException;
import com.aigreentick.services.storage.domain.exception.IllegalMediaStateException;
import com.aigreentick.services.storage.domain.exception.InvalidBatchException;
import com.aigreentick.services.storage.domain.exception.InvalidMediaException;
import com.aigreentick.services.storage.domain.exception.MediaNotFoundException;
import com.aigreentick.services.storage.domain.exception.MediaTooLargeException;
import com.aigreentick.services.storage.domain.exception.QuotaExceededException;
import com.aigreentick.services.storage.domain.exception.QuotaNotProvisionedException;
import com.aigreentick.services.storage.domain.exception.RequestInProgressException;
import com.aigreentick.services.storage.domain.exception.StorageOperationException;
import com.aigreentick.services.storage.domain.exception.TenantAccessDeniedException;
import com.aigreentick.services.storage.domain.exception.UnsupportedStorageOperationException;
import com.aigreentick.services.storage.domain.exception.UploadSessionExpiredException;
import com.aigreentick.services.storage.domain.exception.UploadSessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception → HTTP. The single place a status code is chosen.
 *
 * <p>Client-visible text comes from {@link ErrorCode}; the internal message goes
 * only to the log. The predecessor returned {@code ex.getMessage()} straight to
 * clients for storage and not-found errors, leaking storage keys in 404 bodies.
 *
 * <p>Full mapping table: docs/10-error-handling.md §3.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 400 ─────────────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidMediaException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidMedia(InvalidMediaException ex) {
        log.debug("invalid media: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(QuotaNotProvisionedException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotProvisioned(QuotaNotProvisionedException ex) {
        // INFO, not DEBUG: this is an onboarding failure someone needs to fix.
        log.info("quota not provisioned: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    /**
     * An empty batch is a client mistake, most often a wrong multipart field name.
     * It must never surface as a success envelope reporting zero results.
     */
    @ExceptionHandler(InvalidBatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBatch(InvalidBatchException ex) {
        log.debug("invalid batch: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorBody.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorBody.FieldError(f.getField(), "INVALID", f.getDefaultMessage()))
                .toList();
        log.debug("request validation failed: {} field(s)", details.size());
        return ResponseEntity.badRequest().body(ApiResponse.error(
                new ErrorBody(ErrorCode.REQUEST_INVALID.name(),
                        ErrorCode.REQUEST_INVALID.defaultMessage(), details),
                RequestContext.traceIdOrNull()));
    }

    /**
     * Genuinely malformed requests — the caller sent something Spring could not
     * bind. Each case NAMES what was wrong, because the single opaque
     * "The request is malformed." these used to share made a missing multipart
     * part indistinguishable from an unparseable header, and a caller had no way
     * to tell which of six unrelated problems they had.
     *
     * <p>Present in the predecessor only by omission: a non-numeric path variable
     * or header surfaced as a 500 from an unguarded {@code Long.valueOf}.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("missing multipart part '{}' — check the form-data field name and that "
                + "Content-Type carries a boundary", ex.getRequestPartName());
        return malformed(ex.getRequestPartName(), "MISSING_PART",
                "Required multipart part is missing.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("missing required header '{}'", ex.getHeaderName());
        return malformed(ex.getHeaderName(), "MISSING_HEADER", "Required header is missing.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("missing required parameter '{}'", ex.getParameterName());
        return malformed(ex.getParameterName(), "MISSING_PARAMETER",
                "Required parameter is missing.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("parameter '{}' could not be converted from value [{}]", ex.getName(), ex.getValue());
        return malformed(ex.getName(), "TYPE_MISMATCH", "Value is not of the expected type.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body: {}", ex.getMostSpecificCause().toString());
        return malformed("body", "UNREADABLE", "The request body could not be parsed.");
    }

    /**
     * A RAW {@code IllegalArgumentException}, which is NOT the same thing as a
     * malformed request.
     *
     * <p>The domain throws bare {@code IllegalArgumentException} from several
     * value objects — {@code TenantRef}, {@code ByteSize}, {@code StorageKey},
     * {@code Checksum}, {@code MediaType.fromValue}. When this was bundled with
     * the binding failures above, any one of those surfaced to the caller as
     * "The request is malformed." even when the request was perfectly well formed
     * and the DOMAIN had rejected it. That is actively misleading, and it hid
     * where the failure actually was.
     *
     * <p>Logged at ERROR with the stack trace on purpose: reaching here means a
     * value object rejected input that should have been validated earlier, so the
     * stack trace names the guard that should exist. Every occurrence is a bug to
     * fix, not a caller mistake to report.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("unguarded IllegalArgumentException [trace={}]: {}",
                RequestContext.traceIdOrNull(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.REQUEST_INVALID);
    }

    /**
     * REQUEST_INVALID naming the offending field in {@code details}, reusing the
     * same {@link ErrorBody.FieldError} shape that bean-validation failures
     * already return, so a client parses one structure for both.
     */
    private ResponseEntity<ApiResponse<Void>> malformed(String field, String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(
                new ErrorBody(ErrorCode.REQUEST_INVALID.name(),
                        ErrorCode.REQUEST_INVALID.defaultMessage(),
                        List.of(new ErrorBody.FieldError(field, code, message))),
                RequestContext.traceIdOrNull()));
    }

    // ── 403 / 404 ───────────────────────────────────────────────────────────

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(TenantAccessDeniedException ex) {
        // WARN: either an attack or a broken client. Alertable above a rate threshold.
        log.warn("access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler({MediaNotFoundException.class, UploadSessionNotFoundException.class,
            NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        log.debug("not found: {}", ex.getMessage());
        return ex instanceof DomainException de
                ? build(HttpStatus.NOT_FOUND, de)
                : build(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND);
    }

    // ── 409 ─────────────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalMediaStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalMediaStateException ex) {
        log.info("illegal state: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(RequestInProgressException.class)
    public ResponseEntity<ApiResponse<Void>> handleInProgress(RequestInProgressException ex) {
        log.debug("duplicate request in flight: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "2")
                .body(ApiResponse.error(ErrorBody.of(ex.errorCode().name(), ex.clientMessage()),
                        RequestContext.traceIdOrNull()));
    }

    @ExceptionHandler(UploadSessionExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionExpired(UploadSessionExpiredException ex) {
        log.info("upload session expired: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex);
    }

    // ── 413 / 415 / 422 ─────────────────────────────────────────────────────

    @ExceptionHandler(MediaTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MediaTooLargeException ex) {
        log.debug("file too large: {}", ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ex);
    }

    /** Too many files. 413, alongside the per-file size ceiling. */
    @ExceptionHandler(BatchTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchTooLarge(BatchTooLargeException ex) {
        log.debug("batch too large: {}", ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ex);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.debug("multipart limit exceeded: {}", ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.MEDIA_TOO_LARGE);
    }

    @ExceptionHandler(ContentTypeNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeNotAllowed(ContentTypeNotAllowedException ex) {
        log.info("content type not allowed: {}", ex.getMessage());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex);
    }

    @ExceptionHandler(ContentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(ContentTypeMismatchException ex) {
        // WARN: a mismatch is a signal, possibly an attack, not a formatting problem.
        log.warn("content type mismatch: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        log.warn("idempotency key reused with a different payload: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    // ── 500 / 502 / 507 ─────────────────────────────────────────────────────

    /**
     * 507 is preserved deliberately from the predecessor for downstream
     * compatibility, despite 429 arguably being more conventional.
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuotaExceeded(QuotaExceededException ex) {
        log.info("quota exceeded: {}", ex.getMessage());
        return build(HttpStatus.INSUFFICIENT_STORAGE, ex);
    }

    @ExceptionHandler(StorageOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorage(StorageOperationException ex) {
        // Full detail to the log; the client gets only the generic message.
        log.error("storage operation failed: {}", ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex);
    }

    @ExceptionHandler(UnsupportedStorageOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupported(UnsupportedStorageOperationException ex) {
        log.warn("unsupported storage operation: {}", ex.getMessage());
        return build(HttpStatus.NOT_IMPLEMENTED, ex);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        log.error("unmapped domain exception: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, DomainException ex) {
        return ResponseEntity.status(status).body(ApiResponse.error(
                ErrorBody.of(ex.errorCode().name(), ex.clientMessage()), RequestContext.traceIdOrNull()));
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, ErrorCode code) {
        return ResponseEntity.status(status).body(ApiResponse.error(
                ErrorBody.of(code.name(), code.defaultMessage()), RequestContext.traceIdOrNull()));
    }
}