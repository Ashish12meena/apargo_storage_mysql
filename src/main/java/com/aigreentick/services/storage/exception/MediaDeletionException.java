package com.aigreentick.services.storage.exception;

import java.io.IOException;

public class MediaDeletionException extends RuntimeException {
    public MediaDeletionException(String message){
        super(message);
    }

    public MediaDeletionException(String message, IOException e) {
        super(message);
    }
}
