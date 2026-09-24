package com.aigreentick.services.storage.api.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of {@code errors[]} (API Standard §6).
 *
 * @param field   name as sent in the request: a JSON field ({@code mediaIds[0]}),
 *                query parameter ({@code size}) or multipart part ({@code files})
 * @param code    a standard field code: {@code REQUIRED}, {@code INVALID_FORMAT},
 *                {@code INVALID_VALUE}, {@code TOO_LONG}, {@code OUT_OF_RANGE}, ...
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiFieldError(String field, String code, String message) {
}
