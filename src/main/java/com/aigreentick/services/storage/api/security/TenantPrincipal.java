package com.aigreentick.services.storage.api.security;

import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;

import java.util.Set;

/**
 * VERIFIED caller identity. The only legitimate source of a {@link TenantRef}.
 *
 * <p>Constructed exclusively from a cryptographically verified credential. There
 * is no factory accepting a header value, and none may be added: the predecessor
 * read {@code X-Org-Id} verbatim, so any caller reaching the port could act as any
 * tenant.
 */
public record TenantPrincipal(
        TenantRef tenant,
        String userId,
        Set<Scope> scopes,
        /** JWT {@code jti} — audit correlation and revocation. */
        String tokenId,
        Actor.ActorType actorType,
        /** True when identity came from legacy headers during the migration window. */
        boolean legacy) {

    public TenantPrincipal {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public boolean hasScope(Scope scope) {
        return scopes.contains(scope);
    }

    public Actor asActor(String requestIp) {
        return new Actor(userId, actorType, requestIp);
    }

    public long orgId() {
        return tenant.orgId();
    }

    public long projectId() {
        return tenant.projectId();
    }
}
