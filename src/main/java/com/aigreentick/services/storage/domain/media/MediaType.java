package com.aigreentick.services.storage.domain.media;

import java.util.Locale;

/**
 * Broad classification of stored content.
 *
 * <p>Per-type byte ceilings deliberately do NOT live here. The predecessor
 * hardcoded them in this enum while configuration declared two further, differing
 * sets. Limits live in configuration only (docs/13 §4).
 */
public enum MediaType {

    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT;

    public static MediaType fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (MediaType t : values()) {
            if (t.name().equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown media type: " + raw);
    }

    /** Best-effort classification from a MIME type; DOCUMENT is the fallback. */
    public static MediaType fromMimeType(String mimeType) {
        if (mimeType == null) {
            return DOCUMENT;
        }
        String m = mimeType.toLowerCase(Locale.ROOT);
        if (m.startsWith("image/")) {
            return IMAGE;
        }
        if (m.startsWith("video/")) {
            return VIDEO;
        }
        if (m.startsWith("audio/")) {
            return AUDIO;
        }
        return DOCUMENT;
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
