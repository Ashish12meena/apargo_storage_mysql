package com.aigreentick.services.storage.api.common;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * The only way controllers build a response: one method per row of the
 * status-code table in API Standard §4. Passing one status to both the
 * {@code ResponseEntity} and the wrapper is what keeps them equal.
 */
public final class Responses {

    private Responses() {
    }

    /** 200: read, update with result, action with result (including batch results). */
    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, message, data));
    }

    /** 201 + {@code Location}: resource created. */
    public static <T> ResponseEntity<ApiResponse<T>> created(URI location, String message, T data) {
        return ResponseEntity.created(location).body(ApiResponse.success(HttpStatus.CREATED, message, data));
    }

    /** 202: accepted for background processing; data is {@code {jobId, statusUrl}}. */
    public static <T> ResponseEntity<ApiResponse<T>> accepted(String message, T data) {
        return ResponseEntity.accepted().body(ApiResponse.success(HttpStatus.ACCEPTED, message, data));
    }

    /** 204: done, nothing to return. No body; {@code X-Request-Id} is still sent. */
    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }
}
