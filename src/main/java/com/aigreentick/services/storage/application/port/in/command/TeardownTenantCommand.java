package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.shared.Actor;

/**
 * Removes every file belonging to a project, or to a whole organisation.
 *
 * @param projectId null tears down the ENTIRE organisation — every project under
 *                  it. Deliberately a distinct value rather than a separate
 *                  command, so the wider blast radius is visible at the call site.
 * @param permanent skips the grace period. Use for compliance erasure; leave false
 *                  for cancellations, which are the ones people reverse.
 */
public record TeardownTenantCommand(long orgId, Long projectId, boolean permanent, Actor actor) {

    public boolean isWholeOrg() {
        return projectId == null;
    }
}
