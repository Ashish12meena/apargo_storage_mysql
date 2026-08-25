package com.aigreentick.services.storage.api.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified response envelope. FROZEN shape.
 *
 * <p>{@code status}, {@code message}, and {@code data} are unchanged from the
 * predecessor, so every existing consumer keeps working. {@code error} and
 * {@code traceId} are ADDITIVE and therefore backward-compatible.
 *
 * <p>Filters that run outside the Spring dispatcher must emit this same envelope —
 * the predecessor's rate limiter hand-built JSON with {@code String.format}, which
 * matched by coincidence and would drift the moment the envelope changed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String status, String message, T data, ErrorBody error, String traceId) {

    private static final String SUCCESS = "SUCCESS";
    private static final String ERROR = "ERROR";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS, null, data, null, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS, message, data, null, null);
    }

    public static <T> ApiResponse<T> success(String message, T data, String traceId) {
        return new ApiResponse<>(SUCCESS, message, data, null, traceId);
    }

    public static ApiResponse<Void> error(ErrorBody error, String traceId) {
        return new ApiResponse<>(ERROR, error.message(), null, error, traceId);
    }
}
