package com.aigreentick.services.storage.exception;


public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message) {
        super(message);                      // was missing super() call
    }
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
