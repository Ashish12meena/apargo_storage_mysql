package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.quota.QuotaScope;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.ByteSize;

/**
 * Idempotent quota upsert from the organisation service.
 *
 * <p>Lowering a limit below current usage is ALLOWED and deletes nothing — the
 * tenant simply cannot upload until they free space. Rejecting the change would
 * leave billing and enforcement disagreeing, which is worse.
 *
 * @param projectId null for org scope
 */
public record ProvisionQuotaCommand(QuotaScope scope, long orgId, Long projectId,
                                    ByteSize maxBytes, Actor actor) {
}
