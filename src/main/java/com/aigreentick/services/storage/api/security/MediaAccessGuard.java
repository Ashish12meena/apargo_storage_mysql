package com.aigreentick.services.storage.api.security;

import com.aigreentick.services.storage.application.port.out.MediaRepositoryPort;
import com.aigreentick.services.storage.domain.exception.MediaNotFoundException;
import com.aigreentick.services.storage.domain.exception.TenantAccessDeniedException;
import com.aigreentick.services.storage.domain.media.Media;
import com.aigreentick.services.storage.domain.media.MediaId;
import org.springframework.stereotype.Component;

/**
 * The single choke point for "may this principal do this to this object".
 *
 * <p>One guard rather than per-controller checks: a check that must be remembered
 * in fifteen places will eventually be forgotten in one.
 */
@Component
public class MediaAccessGuard {

    private final MediaRepositoryPort mediaRepository;

    public MediaAccessGuard(MediaRepositoryPort mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public Media requireAccess(MediaId mediaId, TenantPrincipal principal, Scope required) {
        requireScope(principal, required);
        Media media = mediaRepository.findByIdForTenant(mediaId, principal.tenant())
                .orElseThrow(() -> new MediaNotFoundException(
                        "media " + mediaId + " not visible to " + principal.tenant()));
        requireKeyOwnership(media, principal);
        return media;
    }

    /**
     * Scope is checked BEFORE any lookup, so a caller lacking the scope learns
     * nothing about whether the resource exists.
     */
    public void requireScope(TenantPrincipal principal, Scope required) {
        if (!principal.hasScope(required)) {
            throw new TenantAccessDeniedException(
                    "principal lacks scope " + required.value());
        }
    }

    /**
     * Asserts the storage key lies under the principal's own prefix.
     *
     * <p>Required on every path that resolves a key to bytes. The predecessor's
     * serve controller had only path-traversal protection and relied entirely on
     * UUIDs being unguessable, so a key leaked through a log line, a Referer
     * header, or a shared link was a cross-tenant read.
     */
    public void requireKeyOwnership(Media media, TenantPrincipal principal) {
        if (!media.storageKey().belongsTo(principal.tenant())) {
            // 404, not 403: a 403 confirms existence and creates an enumeration oracle.
            throw new MediaNotFoundException(
                    "storage key outside tenant prefix for media " + media.id());
        }
    }
}
