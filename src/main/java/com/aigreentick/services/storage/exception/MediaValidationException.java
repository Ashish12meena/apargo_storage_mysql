package com.aigreentick.services.storage.exception;


public class MediaValidationException extends RuntimeException {
    public MediaValidationException(String message) { super(message); }
    public MediaValidationException(String message, Throwable cause) { super(message, cause); }
}
