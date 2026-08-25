package com.aigreentick.services.storage.domain.media;

import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.util.Locale;
import java.util.UUID;

/**
 * Location of an object within a storage backend:
 * {@code org-{orgId}/proj-{projectId}/{mediaType}/{uuid}[.ext]}.
 *
 * <p>CAPABILITY-BEARING until an ownership check is applied. Every read path must
 * call {@link #belongsTo(TenantRef)}; a leaked key is otherwise a cross-tenant
 * read. Keys are therefore treated as secrets: never returned in an API response,
 * never logged above DEBUG.
 */
public record StorageKey(String value) {

    /**
     * Matches the {@code storage_key} column. Kept at 512 so a UNIQUE index fits
     * InnoDB's 3072-byte limit under utf8mb4 — real keys are around 60 characters.
     * Rejecting here means an over-long key fails validation, not an INSERT.
     */
    private static final int MAX_LENGTH = 512;

    public StorageKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("storage key must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("storage key exceeds " + MAX_LENGTH + " characters");
        }
        if (value.startsWith("/") || value.contains("..") || value.contains("\\")
                || value.indexOf('\0') >= 0 || value.contains("//")) {
            throw new IllegalArgumentException("storage key contains an illegal path sequence");
        }
    }

    /** Server-side construction. A client-supplied key is never used for a write. */
    public static StorageKey generate(TenantRef tenant, MediaType type, String extension) {
        String ext = normaliseExtension(extension);
        return new StorageKey(tenant.storagePrefix() + type.lower() + "/" + UUID.randomUUID() + ext);
    }

    private static String normaliseExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String e = extension.trim().toLowerCase(Locale.ROOT);
        if (!e.startsWith(".")) {
            e = "." + e;
        }
        // Defensive: an extension is appended to a path, so it must not carry one.
        if (e.contains("/") || e.contains("\\") || e.contains("..") || e.length() > 12) {
            return "";
        }
        return e;
    }

    /** True only if this key sits under the tenant's own prefix. */
    public boolean belongsTo(TenantRef tenant) {
        return value.startsWith(tenant.storagePrefix());
    }

    public String filename() {
        int slash = value.lastIndexOf('/');
        return slash < 0 ? value : value.substring(slash + 1);
    }

    public String extension() {
        String name = filename();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }
}
