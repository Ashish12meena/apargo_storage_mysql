package com.aigreentick.services.storage.application.port.in;

import com.aigreentick.services.storage.application.port.in.command.BatchProxiedUploadCommand;
import com.aigreentick.services.storage.application.port.in.result.BatchUploadView;

/**
 * Many small files in one request.
 *
 * <p>Separate from {@link UploadMediaUseCase} rather than a fifth method on it:
 * batch is a sequential loop OVER that port, not a new upload mode, and keeping
 * the ports apart is what makes that visible in the type signature.
 */
public interface BatchUploadMediaUseCase {

    BatchUploadView uploadBatch(BatchProxiedUploadCommand command);
}
