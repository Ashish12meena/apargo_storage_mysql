package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;
import com.aigreentick.services.storage.domain.shared.TenantRef;

/**
 * Phase one of a direct-to-storage upload.
 *
 * <p>{@code declaredSize} is a claim used for quota reservation and for the
 * presigned URL's {@code content-length-range} condition. It is verified against
 * the actual object at completion, and storage itself rejects an oversized PUT,
 * so a client cannot under-declare to evade quota.
 */
public record InitiateUploadCommand(
        TenantRef tenant,
        Actor actor,
        String originalFilename,
        String declaredContentType,
        ByteSize declaredSize,
        String idempotencyKey) {
}
