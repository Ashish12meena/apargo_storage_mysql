package com.aigreentick.services.storage.exception;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String message){
        super(message);
    }
}
