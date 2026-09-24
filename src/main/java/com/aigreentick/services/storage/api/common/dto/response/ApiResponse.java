package com.aigreentick.services.storage.api.common.dto.response;

import com.aigreentick.services.storage.common.context.RequestContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * The single response wrapper for every JSON body, success or error
 * (company API Standard §4). Same shape as template-service's
 * {@code ApiEnvelope}, so one client function handles both services.
 *
 * <pre>
 * { "success", "status", "code", "message", "data", "errors", "meta": { "requestId", "timestamp", "path"? } }
 * </pre>
 *
 * <h2>Invariants, enforced by construction</h2>
 * <ul>
 *   <li>{@code status} equals the HTTP status and {@code success} is true
 *       exactly for 2xx: both come from the one status the caller also puts on
 *       the response ({@link com.aigreentick.services.storage.api.common.Responses},
 *       {@code GlobalExceptionHandler}, {@code ErrorResponseWriter}).</li>
 *   <li>Success has {@code code = "SUCCESS"} and {@code errors = []}; error has
 *       {@code data = null} and {@code meta.path}.</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(ALWAYS)}: {@code data: null} and {@code errors: []} are
 * part of the contract and must be present; payload DTOs inside {@code data}
 * keep their own inclusion rules.
 *
 * <p>Replaces the pre-standard {@code {status: "SUCCESS"|"ERROR", message, data,
 * error, traceId}} envelope (ADR-015).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"success", "status", "code", "message", "data", "errors", "meta"})
public record ApiResponse<T>(
        boolean success,
        int status,
        String code,
        String message,
        T data,
        List<ApiFieldError> errors,
        Meta meta) {

    public static final String SUCCESS_CODE = "SUCCESS";

    public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
        if (!status.is2xxSuccessful()) {
            throw new IllegalArgumentException("success wrapper needs a 2xx status, got " + status);
        }
        return new ApiResponse<>(true, status.value(), SUCCESS_CODE, message, data, List.of(), Meta.now(null));
    }

    public static ApiResponse<Void> error(int status, String code, String message,
                                          List<ApiFieldError> errors, String path) {
        if (status < 400) {
            throw new IllegalArgumentException("error wrapper needs a 4xx/5xx status, got " + status);
        }
        return new ApiResponse<>(false, status, code, message, null,
                errors == null ? List.of() : List.copyOf(errors), Meta.now(path));
    }

    /** {@code path} is set on errors only. */
    @JsonPropertyOrder({"requestId", "timestamp", "path"})
    public record Meta(
            String requestId,
            Instant timestamp,
            @JsonInclude(JsonInclude.Include.NON_NULL) String path) {

        static Meta now(String path) {
            return new Meta(RequestContext.requestIdOrNull(), Instant.now(), path);
        }
    }
}
