package com.aigreentick.services.storage.domain.media;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SHA-256 of the stored bytes. Used for integrity verification and
 * reconciliation, NOT for deduplication (ADR-011).
 */
public record Checksum(String sha256Hex) {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    public Checksum {
        if (sha256Hex == null || !HEX_64.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException("checksum must be 64 lowercase hex characters");
        }
    }

    public static Checksum of(String hex) {
        return new Checksum(hex == null ? null : hex.toLowerCase(Locale.ROOT));
    }

    public static Checksum ofNullable(String hex) {
        return hex == null || hex.isBlank() ? null : of(hex);
    }
}
