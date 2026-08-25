package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;

/**
 * @param permanent skips the recovery grace period; requires
 *                  {@code media:delete:permanent}. For compliance erasure, not
 *                  routine deletion.
 */
public record DeleteMediaCommand(MediaId mediaId, TenantRef tenant, Actor actor, boolean permanent) {
}
