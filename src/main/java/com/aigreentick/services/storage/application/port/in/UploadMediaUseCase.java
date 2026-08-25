package com.aigreentick.services.storage.application.port.in;

import com.aigreentick.services.storage.application.port.in.command.CompleteUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.InitiateUploadCommand;
import com.aigreentick.services.storage.application.port.in.command.ProxiedUploadCommand;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.application.port.in.result.UploadTicket;
import com.aigreentick.services.storage.domain.shared.TenantRef;

/**
 * The two upload paths, behind one port. The predecessor shipped two
 * fully-implemented orchestrators with different concurrency models, only one of
 * which was wired (ADR-002).
 */
public interface UploadMediaUseCase {

    /** Small files. Bytes traverse this service. Idempotent on the key. */
    MediaView uploadProxied(ProxiedUploadCommand command);

    /** Reserves quota, creates a PENDING record, returns presigned URL(s). */
    UploadTicket initiate(InitiateUploadCommand command);

    /** Verifies, inspects, activates. Idempotent — a repeat returns the same result. */
    MediaView complete(CompleteUploadCommand command);

    /** Client-initiated abandonment. Releases quota, removes any partial object. */
    void abort(String uploadSessionId, TenantRef tenant);
}
