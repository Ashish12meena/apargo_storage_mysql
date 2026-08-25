package com.aigreentick.services.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps {@code org_storage}.
 *
 * <p>{@code version} is a PLAIN column, deliberately not {@code @Version}.
 * Reservation and release go through conditional bulk updates that manage the
 * column explicitly; Hibernate's automatic version checking applies only to
 * entity-managed writes and would silently do nothing for those statements while
 * implying that it did. One mechanism, visible in the SQL.
 */
@Entity
@Table(name = "org_storage")
public class OrgStorageEntity {

    @Id
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "max_bytes", nullable = false)
    private Long maxBytes;

    @Column(name = "used_bytes", nullable = false)
    private Long usedBytes;

    @Column(name = "version", nullable = false)
    private Long version;

    // Written by the upsert statement, not by a database DEFAULT: Hibernate does
    // not generate DEFAULT CURRENT_TIMESTAMP, so relying on one would break the
    // generated schema.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrgStorageEntity() {
    }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public Long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(Long v) { this.maxBytes = v; }
    public Long getUsedBytes() { return usedBytes; }
    public void setUsedBytes(Long v) { this.usedBytes = v; }
    public Long getVersion() { return version; }
    public void setVersion(Long v) { this.version = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
