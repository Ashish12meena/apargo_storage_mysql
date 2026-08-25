package com.aigreentick.services.storage.domain.media;

import java.util.Locale;
import java.util.Map;

/**
 * A MIME type, distinguishing what the client CLAIMED from what the bytes ARE.
 * {@link #detected()} is what gets persisted, served, and returned.
 */
public record ContentType(String declared, String detected) {

    private static final String OCTET_STREAM = "application/octet-stream";

    /** Aliases normalised in ONE place. {@code image/jpg} is not a real MIME type. */
    private static final Map<String, String> ALIASES = Map.of(
            "image/jpg", "image/jpeg",
            "image/pjpeg", "image/jpeg",
            "image/x-png", "image/png",
            "audio/mp3", "audio/mpeg",
            "audio/x-m4a", "audio/mp4",
            "video/x-mp4", "video/mp4",
            "application/x-pdf", "application/pdf",
            "text/xml", "application/xml");

    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return OCTET_STREAM;
        }
        String base = raw.trim().toLowerCase(Locale.ROOT);
        int semicolon = base.indexOf(';');
        if (semicolon > 0) {
            base = base.substring(0, semicolon).trim();
        }
        return ALIASES.getOrDefault(base, base);
    }

    public static ContentType of(String declared, String detected) {
        return new ContentType(normalise(declared), normalise(detected));
    }

    /**
     * True when declared and detected agree after normalisation.
     *
     * <p>A declared {@code application/octet-stream} is treated as "no claim
     * made" rather than a conflicting claim: many HTTP clients send it as a
     * default. The detected type still has to pass the allowlist, so this
     * concession does not widen what can be stored.
     */
    public boolean isConsistent() {
        if (declared == null || declared.equals(OCTET_STREAM)) {
            return true;
        }
        return declared.equals(detected);
    }

    public boolean detectedIsOctetStream() {
        return OCTET_STREAM.equals(detected);
    }
}
