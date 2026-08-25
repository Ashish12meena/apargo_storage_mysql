package com.aigreentick.services.storage.application.port.in;

import com.aigreentick.services.storage.application.port.in.command.DeleteMediaCommand;
import com.aigreentick.services.storage.application.port.in.command.RestoreMediaCommand;
import com.aigreentick.services.storage.application.port.in.result.MediaView;

/**
 * Deletion — the operation this service is named for and which the predecessor
 * never exposed. Soft delete and physical removal are separate steps: deleting
 * inside the request transaction risks a rollback after the object is already
 * gone, which is unrecoverable.
 */
public interface DeleteMediaUseCase {

    /** ACTIVE → DELETED, releases quota, emits {@code media.deleted}. Idempotent. */
    void delete(DeleteMediaCommand command);

    /** DELETED → ACTIVE within the grace period. */
    MediaView restore(RestoreMediaCommand command);
}
