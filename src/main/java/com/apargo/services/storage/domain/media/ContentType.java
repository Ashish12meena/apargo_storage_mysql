package com.apargo.services.storage.domain.media;

import java.util.Locale;
import java.util.Map;

/**
 * A MIME type, distinguishing what the client CLAIMED from what the bytes ARE.
 * {@link #detected()} is what gets persisted, served, and returned.
 */
public record ContentType(String declared, String detected) {

    private static final String OCTET_STREAM = "application/octet-stream";

    /**
     * Aliases normalised in ONE place. {@code image/jpg} is not a real MIME type.
     *
     * <p>The codec names Tika reports for an Ogg file — {@code audio/opus},
     * {@code audio/vorbis} — are aliased to the container, {@code audio/ogg}: that is
     * the registered type for an Ogg file whatever codec it carries (RFC 7845), and
     * it is what WhatsApp labels every voice note. Without it each voice note was
     * refused as a type mismatch. {@code audio/x-aac} is Tika's name for ADTS AAC.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("image/jpg", "image/jpeg"),
            Map.entry("image/pjpeg", "image/jpeg"),
            Map.entry("image/x-png", "image/png"),
            Map.entry("audio/mp3", "audio/mpeg"),
            Map.entry("audio/x-m4a", "audio/mp4"),
            Map.entry("audio/opus", "audio/ogg"),
            Map.entry("audio/vorbis", "audio/ogg"),
            Map.entry("audio/x-aac", "audio/aac"),
            Map.entry("video/x-mp4", "video/mp4"),
            Map.entry("application/x-pdf", "application/pdf"),
            Map.entry("text/xml", "application/xml"));

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
