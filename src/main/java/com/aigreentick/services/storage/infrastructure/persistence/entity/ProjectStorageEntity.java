package com.aigreentick.services.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps {@code project_storage}.
 *
 * <p>No {@code @ManyToOne} to {@link OrgStorageEntity}, even though the foreign key
 * exists. Quota is read and written by primary key only; an association would add
 * a lazy-loading proxy and a potential extra query on the hottest write path in the
 * service, buying nothing.
 *
 * @see OrgStorageEntity for why {@code version} is not {@code @Version}
 */
@Entity
@Table(name = "project_storage")
@IdClass(ProjectStorageId.class)
public class ProjectStorageEntity {

    @Id
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Id
    @Column(name = "project_id", nullable = false)
    private Long projectId;

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

    protected ProjectStorageEntity() {
    }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
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
