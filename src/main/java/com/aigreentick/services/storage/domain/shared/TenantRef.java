package com.aigreentick.services.storage.domain.shared;

/**
 * The tenant an operation is scoped to. Constructed only from VERIFIED
 * credentials — never from a header, path variable, or request body.
 */
public record TenantRef(long orgId, long projectId) {

    public TenantRef {
        if (orgId <= 0) {
            throw new IllegalArgumentException("orgId must be positive");
        }
        if (projectId <= 0) {
            throw new IllegalArgumentException("projectId must be positive");
        }
    }

    /** Storage-key prefix owned by this tenant. */
    public String storagePrefix() {
        return "org-" + orgId + "/proj-" + projectId + "/";
    }

    @Override
    public String toString() {
        return "org=" + orgId + ",project=" + projectId;
    }
}
