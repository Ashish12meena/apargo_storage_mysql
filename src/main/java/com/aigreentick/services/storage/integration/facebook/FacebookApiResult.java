package com.aigreentick.services.storage.integration.facebook;

import lombok.Getter;

/**
 * Wraps results from Facebook Graph API calls.
 */
@Getter
public class FacebookApiResult<T> {
    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final int statusCode;

    private FacebookApiResult(boolean success, T data, String errorMessage, int statusCode) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
    }

    public static <T> FacebookApiResult<T> success(T data, int statusCode) {
        return new FacebookApiResult<>(true, data, null, statusCode);
    }

    public static <T> FacebookApiResult<T> error(String message, int statusCode) {
        return new FacebookApiResult<>(false, null, message, statusCode);
    }
}
