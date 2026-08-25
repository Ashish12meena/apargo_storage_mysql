package com.aigreentick.services.storage.api.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * @param code    stable {@code ErrorCode} name — clients branch on this
 * @param message safe, human-readable; never a bucket name, storage key, path,
 *                SQL fragment, or upstream response body
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String code, String message, List<FieldError> details) {

    public record FieldError(String field, String code, String message) {
    }

    public static ErrorBody of(String code, String message) {
        return new ErrorBody(code, message, null);
    }
}
