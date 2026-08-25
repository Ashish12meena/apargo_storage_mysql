package com.aigreentick.services.storage.api.v1.media.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Per-item outcome in a batch. One failure never fails the batch. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchItemResult(String id, boolean success, String errorCode, String message) {

    public static BatchItemResult ok(String id) {
        return new BatchItemResult(id, true, null, null);
    }

    public static BatchItemResult failed(String id, String errorCode, String message) {
        return new BatchItemResult(id, false, errorCode, message);
    }
}
