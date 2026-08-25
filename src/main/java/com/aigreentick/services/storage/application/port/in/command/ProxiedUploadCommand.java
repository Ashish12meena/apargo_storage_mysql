package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/**
 * Small-file upload where bytes pass through this service.
 *
 * <p>Content is a {@code Supplier<InputStream>} rather than a {@code MultipartFile}:
 * it keeps the servlet type out of the application layer and can be re-opened for
 * a retry. Size is known from the multipart header, so there is no need to spool
 * to a temp file merely to learn the content length — the predecessor's temp-file
 * round trip cost a full extra disk write and read on every upload.
 *
 * @throws UncheckedIOException from {@code content.get()} if the stream cannot be opened
 */
public record ProxiedUploadCommand(
        TenantRef tenant,
        Actor actor,
        String originalFilename,
        String declaredContentType,
        ByteSize size,
        Supplier<InputStream> content,
        String idempotencyKey) {
}
