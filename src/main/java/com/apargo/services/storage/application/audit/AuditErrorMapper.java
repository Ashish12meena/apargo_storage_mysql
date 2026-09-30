package com.apargo.services.storage.application.audit;

import com.apargo.platform.contract.audit.AuditErrorCategory;
import com.apargo.platform.contract.audit.AuditErrorDto;
import com.apargo.services.storage.common.error.ErrorCode;
import com.apargo.services.storage.domain.exception.DomainException;

/**
 * Maps a failure to the audit {@code error} block (Implementation Guide §9).
 *
 * <ul>
 *   <li>{@code code} is the service's own {@link ErrorCode}.</li>
 *   <li>The real message is kept only for BUSINESS and VALIDATION, and even then
 *       it is the client-safe message, never the internal one (which may carry a
 *       storage key or provider detail). Other categories get a generic message.</li>
 *   <li>{@code reference} is the trace id. Never a stack trace or upstream body.</li>
 * </ul>
 */
public final class AuditErrorMapper {

    private AuditErrorMapper() {
    }

    public static AuditErrorDto errorFrom(Throwable failure, String traceId) {
        if (failure instanceof DomainException domain && domain.errorCode() != null) {
            ErrorCode code = domain.errorCode();
            AuditErrorCategory category = categoryOf(code);
            return new AuditErrorDto(category, code.name(), messageFor(category, domain.clientMessage()),
                    null, traceId);
        }
        return new AuditErrorDto(AuditErrorCategory.SYSTEM, ErrorCode.INTERNAL_ERROR.name(),
                AuditConstants.Messages.SYSTEM_FAILURE, null, traceId);
    }

    /**
     * Exhaustive on purpose: a new {@link ErrorCode} does not compile until it is
     * given a category here.
     */
    public static AuditErrorCategory categoryOf(ErrorCode code) {
        return switch (code) {
            case BAD_REQUEST, METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE, VALIDATION_FAILED,
                 MEDIA_INVALID, MEDIA_TOO_LARGE, CONTENT_TYPE_NOT_ALLOWED, CONTENT_TYPE_MISMATCH,
                 IDEMPOTENCY_KEY_REQUIRED, BATCH_FILES_REQUIRED, BATCH_TOO_MANY_FILES -> AuditErrorCategory.VALIDATION;
            case UNAUTHENTICATED -> AuditErrorCategory.AUTHENTICATION;
            case FORBIDDEN -> AuditErrorCategory.AUTHORIZATION;
            case DEPENDENCY_FAILURE, STORAGE_UNAVAILABLE, BATCH_ITEM_SKIPPED -> AuditErrorCategory.EXTERNAL_SERVICE;
            case INTERNAL_ERROR, SERVICE_UNAVAILABLE, OPERATION_UNSUPPORTED -> AuditErrorCategory.SYSTEM;
            case NOT_FOUND, RATE_LIMITED, MEDIA_NOT_FOUND, MEDIA_ILLEGAL_STATE, IDEMPOTENCY_KEY_REUSED,
                 IDEMPOTENCY_KEY_IN_PROGRESS, UPLOAD_SESSION_EXPIRED, UPLOAD_SESSION_NOT_FOUND,
                 QUOTA_EXCEEDED, QUOTA_NOT_PROVISIONED, QUOTA_LIMIT_INVALID -> AuditErrorCategory.BUSINESS;
        };
    }

    private static String messageFor(AuditErrorCategory category, String clientMessage) {
        return switch (category) {
            case BUSINESS, VALIDATION -> clientMessage;
            case EXTERNAL_SERVICE -> AuditConstants.Messages.EXTERNAL_SERVICE_FAILURE;
            case AUTHENTICATION -> AuditConstants.Messages.AUTHENTICATION_FAILURE;
            case AUTHORIZATION -> AuditConstants.Messages.AUTHORIZATION_FAILURE;
            case SYSTEM -> AuditConstants.Messages.SYSTEM_FAILURE;
        };
    }
}
