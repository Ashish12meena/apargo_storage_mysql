package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * A batch of small-file uploads submitted in ONE request.
 *
 * <p>Exists because {@code template-service} downloads media in batches and would
 * otherwise open one HTTP request per file. Collapsing the batch is the entire
 * benefit; it is achieved by this endpoint existing, not by concurrency inside it.
 *
 * <p>Each file carries content as a re-openable {@code Supplier<InputStream>}, the
 * same shape {@link ProxiedUploadCommand} uses, so no servlet type crosses into
 * the application layer and nothing is spooled to a temp file to learn its length.
 *
 * @param idempotencyKey the BATCH-level key, or null. Per-file keys are derived
 *                       from it — passing it unchanged to every file would fail on
 *                       the second file, because the guard fingerprints filename,
 *                       type and size alongside the key and a differing fingerprint
 *                       under the same key is a conflict.
 */
public record BatchProxiedUploadCommand(
        TenantRef tenant,
        Actor actor,
        List<FileItem> files,
        String idempotencyKey) {

    public BatchProxiedUploadCommand {
        files = files == null ? List.of() : List.copyOf(files);
    }

    /**
     * @param originalFilename exactly as the caller supplied it in the multipart
     *                         part. It is the join key the client matches results
     *                         against its own tasks, so it is never replaced by a
     *                         storage key, a temp-file name, or a normalised variant.
     */
    public record FileItem(
            String originalFilename,
            String declaredContentType,
            ByteSize size,
            Supplier<InputStream> content) {
    }

    /**
     * Per-file idempotency key.
     *
     * <p>The INDEX is included rather than the filename alone: two files in one
     * batch may legitimately share a filename, and keying on the name would make
     * the second one collide with the first.
     *
     * <p>Null batch key yields null per-file keys, matching single-file behaviour
     * where an absent header runs the upload unguarded.
     */
    public String idempotencyKeyForIndex(int index) {
        return idempotencyKey == null || idempotencyKey.isBlank()
                ? null
                : idempotencyKey + ":" + index;
    }
}
