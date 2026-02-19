package com.aigreentick.services.storage.exception;


public class MediaUploadException extends RuntimeException {
    private final int statusCode;

    public MediaUploadException(String message) {
        super(message);
        this.statusCode = 500;
    }
    public MediaUploadException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }
    public MediaUploadException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}