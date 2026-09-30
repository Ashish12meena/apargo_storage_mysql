package com.apargo.services.storage.application.port.in.command;

import com.apargo.services.storage.domain.media.MediaId;
import com.apargo.services.storage.domain.shared.Actor;
import com.apargo.services.storage.domain.shared.TenantRef;

/**
 * @param permanent skips the recovery grace period; requires
 *                  {@code media:delete:permanent}. For compliance erasure, not
 *                  routine deletion.
 */
public record DeleteMediaCommand(MediaId mediaId, TenantRef tenant, Actor actor, boolean permanent) {
}
