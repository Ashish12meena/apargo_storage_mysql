package com.aigreentick.services.storage.application.shared;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque keyset cursor: {@code (createdAt, id)}, the total order backing
 * {@code idx_media_keyset}. Base64 so callers do not parse or construct it.
 *
 * <p>Not signed. A tampered cursor can only shift the caller's own window within
 * their own tenant — every query is tenant-scoped regardless — so the worst case
 * is a malformed page, not a disclosure.
 */
public record Cursor(Instant createdAt, long id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt.toEpochMilli() + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            int sep = decoded.indexOf(':');
            if (sep <= 0) {
                return null;
            }
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(decoded.substring(0, sep))),
                    Long.parseLong(decoded.substring(sep + 1)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
