package com.aigreentick.services.storage.application.service;

import com.aigreentick.services.storage.config.properties.MediaValidationProperties;
import com.aigreentick.services.storage.domain.exception.ContentTypeMismatchException;
import com.aigreentick.services.storage.domain.exception.ContentTypeNotAllowedException;
import com.aigreentick.services.storage.domain.exception.InvalidMediaException;
import com.aigreentick.services.storage.domain.exception.MediaTooLargeException;
import com.aigreentick.services.storage.domain.media.ContentType;
import com.aigreentick.services.storage.domain.media.MediaType;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Validation in cost order: cheapest and most likely to fail first, so an attacker
 * cannot make the service do expensive work before rejection.
 *
 * <p>Limits are global and identical for every tenant (ADR-012). Total storage
 * capacity varies per project, but that lives in the database and is enforced by
 * quota reservation, not here.
 *
 * <p>This service checks SIZE and TYPE only. It does not inspect document
 * structure — no pixel counts, no page counts (ADR-013).
 */
@Service
@Slf4j
public class MediaValidationService {

    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String OCTET_STREAM = "application/octet-stream";

    private final MediaValidationProperties properties;

    public MediaValidationService(MediaValidationProperties properties) {
        this.properties = properties;
        if (properties.allowedMimeTypesByMediaType().isEmpty()) {
            // An empty allowlist rejects everything. Failing at boot is better than
            // every upload returning 415 with no obvious cause.
            throw new IllegalStateException(
                    "media.validation.allowed-mime-types-by-media-type is empty; no upload could succeed");
        }
        log.info("upload limits: max={} bytes, per-type overrides={}, allowlist={}",
                properties.maxBytes(), properties.maxBytesByMediaType().keySet(),
                properties.allowedMimeTypesByMediaType().keySet());
    }

    /** Pre-flight checks, before any quota is reserved or byte is written. */
    public MediaType validateBeforeUpload(String originalFilename, String declaredContentType, ByteSize size) {
        validateFilename(originalFilename);

        String declared = ContentType.normalise(declaredContentType);
        MediaType mediaType = MediaType.fromMimeType(declared);
        validateSize(mediaType, size);

        // The declared type only gates obviously-disallowed uploads. The
        // authoritative check runs against the detected type once bytes exist.
        if (!OCTET_STREAM.equals(declared) && !isAllowed(mediaType, declared)) {
            throw new ContentTypeNotAllowedException("declared type not allowed: " + declared);
        }
        return mediaType;
    }

    /**
     * Authoritative check against the DETECTED type. A mismatch is a rejection,
     * never a silent correction: it is a signal, not a formatting problem.
     */
    public MediaType validateDetected(ContentType contentType, ByteSize actualSize) {
        if (properties.rejectOnTypeMismatch() && !contentType.isConsistent()) {
            throw new ContentTypeMismatchException(
                    "declared=" + contentType.declared() + " detected=" + contentType.detected());
        }
        MediaType mediaType = MediaType.fromMimeType(contentType.detected());
        if (!isAllowed(mediaType, contentType.detected())) {
            throw new ContentTypeNotAllowedException("detected type not allowed: " + contentType.detected());
        }
        validateSize(mediaType, actualSize);
        return mediaType;
    }

    public void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new InvalidMediaException("filename is blank");
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidMediaException("filename exceeds " + MAX_FILENAME_LENGTH + " characters");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")
                || filename.indexOf('\0') >= 0) {
            throw new InvalidMediaException("filename contains an illegal path sequence");
        }
    }

    private void validateSize(MediaType mediaType, ByteSize size) {
        if (size == null || size.isZero()) {
            throw new InvalidMediaException("file is empty");
        }
        long limit = properties.limitFor(mediaType.name());
        if (size.value() > limit) {
            throw new MediaTooLargeException(
                    "size " + size.value() + " exceeds the " + mediaType + " limit of " + limit);
        }
    }

    private boolean isAllowed(MediaType mediaType, String mimeType) {
        List<String> allowed = properties.allowedMimeTypesByMediaType().get(mediaType.name());
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        return allowed.stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).equals(mimeType));
    }

    public int inspectionHeaderBytes() {
        return properties.inspectionHeaderBytes();
    }

    public long maxBytes() {
        return properties.maxBytes();
    }

    /** Extension from the original filename, for the storage key only. */
    public String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
