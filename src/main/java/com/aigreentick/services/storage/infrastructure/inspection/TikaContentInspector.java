package com.aigreentick.services.storage.infrastructure.inspection;

import com.aigreentick.services.storage.application.port.out.ContentInspectorPort;
import com.aigreentick.services.storage.domain.media.ContentType;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Magic-byte detection over a bounded prefix of the file.
 *
 * <p>The predecessor's only content check was the {@code Content-Type} the client
 * put in the multipart header; nothing read the file. An HTML document with
 * embedded script labelled {@code image/png} passed validation and was stored as
 * an image.
 *
 * <p>Cost is flat in file size: the same 8 KB is examined for a 5 KB file and a
 * 500 MB one.
 */
@Component
@Slf4j
public class TikaContentInspector implements ContentInspectorPort {

    private static final String OCTET_STREAM = "application/octet-stream";

    private final Detector detector;

    public TikaContentInspector() {
        this.detector = TikaConfig.getDefaultConfig().getDetector();
    }

    @Override
    public ContentType inspect(byte[] header, String declaredType, String filename) {
        return ContentType.of(declaredType, detect(header, filename));
    }

    private String detect(byte[] header, String filename) {
        if (header == null || header.length == 0) {
            return OCTET_STREAM;
        }
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isBlank()) {
            // A filename hint only refines a magic-byte match; it never overrides
            // one, so a misleading extension cannot smuggle a type through.
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        try (InputStream stream = new ByteArrayInputStream(header)) {
            MediaType type = detector.detect(stream, metadata);
            return type == null ? OCTET_STREAM : type.getBaseType().toString();
        } catch (IOException e) {
            log.warn("content detection failed, treating as octet-stream: {}", e.toString());
            return OCTET_STREAM;
        }
    }
}
