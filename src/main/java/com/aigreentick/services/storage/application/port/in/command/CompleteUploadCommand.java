package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.util.List;

/**
 * Phase two: the client asserts the bytes have landed. The assertion is verified,
 * never trusted — head the object, compare size, inspect the leading bytes. The
 * record is not readable through any API until this passes.
 */
public record CompleteUploadCommand(
        TenantRef tenant,
        Actor actor,
        String uploadSessionId,
        List<PartETag> parts) {

    public record PartETag(int partNumber, String etag) {
    }
}
