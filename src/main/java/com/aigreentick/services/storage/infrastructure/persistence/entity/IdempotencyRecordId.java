package com.aigreentick.services.storage.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@code idempotency_record}.
 *
 * <p>Tenant-scoped by design: keys cannot collide or be probed across
 * organisations.
 */
public class IdempotencyRecordId implements Serializable {

    private Long orgId;
    private Long projectId;
    private String idempotencyKey;

    public IdempotencyRecordId() {
    }

    public IdempotencyRecordId(Long orgId, Long projectId, String idempotencyKey) {
        this.orgId = orgId;
        this.projectId = projectId;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyRecordId other)) {
            return false;
        }
        return Objects.equals(orgId, other.orgId)
                && Objects.equals(projectId, other.projectId)
                && Objects.equals(idempotencyKey, other.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, projectId, idempotencyKey);
    }
}
