package com.aigreentick.services.storage.application.port.out;

import com.aigreentick.services.storage.domain.media.ContentType;

/**
 * Determines what a file ACTUALLY is, from its leading bytes.
 *
 * <p>The predecessor's only content check was the {@code Content-Type} the client
 * put in the multipart part header; nothing read the file. An HTML document with
 * embedded script labelled {@code image/png} passed validation and was stored as
 * an image.
 */
public interface ContentInspectorPort {

    /**
     * @param header       leading bytes, up to the configured inspection window
     * @param declaredType what the client claimed
     * @param filename     original filename — a weak secondary hint only
     */
    ContentType inspect(byte[] header, String declaredType, String filename);
}
