package com.aigreentick.services.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Maps {@code upload_session}. */
@Entity
@Table(name = "upload_session",
        indexes = {
                // The sweeper's only query. Without it the sweep degrades to a full
                // scan as sessions accumulate.
                @Index(name = "idx_session_sweep", columnList = "status, expires_at"),
                @Index(name = "idx_session_tenant", columnList = "org_id, project_id")
        })
public class UploadSessionEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "media_id")
    private Long mediaId;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "mode", nullable = false, length = 30)
    private String mode;

    @Column(name = "declared_size", nullable = false)
    private Long declaredSize;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "provider_upload_id", length = 255)
    private String providerUploadId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected UploadSessionEntity() {
    }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long v) { this.mediaId = v; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String v) { this.storageKey = v; }
    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public Long getDeclaredSize() { return declaredSize; }
    public void setDeclaredSize(Long v) { this.declaredSize = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getProviderUploadId() { return providerUploadId; }
    public void setProviderUploadId(String v) { this.providerUploadId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
}
