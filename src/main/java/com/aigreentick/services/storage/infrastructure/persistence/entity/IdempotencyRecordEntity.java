package com.aigreentick.services.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/** Maps {@code idempotency_record}. */
@Entity
@Table(name = "idempotency_record",
        indexes = @Index(name = "idx_idem_expiry", columnList = "expires_at"))
@IdClass(IdempotencyRecordId.class)
public class IdempotencyRecordEntity {

    @Id
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Id
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "json")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecordEntity() {
    }

    public IdempotencyRecordEntity(Long orgId, Long projectId, String idempotencyKey,
                                   String requestHash, String status, Instant createdAt,
                                   Instant expiresAt) {
        this.orgId = orgId;
        this.projectId = projectId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getOrgId() { return orgId; }
    public Long getProjectId() { return projectId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer v) { this.responseStatus = v; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String v) { this.responseBody = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
