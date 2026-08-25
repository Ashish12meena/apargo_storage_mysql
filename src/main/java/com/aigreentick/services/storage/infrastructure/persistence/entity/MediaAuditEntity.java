package com.aigreentick.services.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps {@code media_audit}. Insert-only — the application's database role holds no
 * UPDATE or DELETE grant on this table.
 */
@Entity
@Table(name = "media_audit",
        indexes = {
                @Index(name = "idx_audit_tenant_time", columnList = "org_id, project_id, occurred_at DESC"),
                @Index(name = "idx_audit_resource", columnList = "resource_id")
        })
public class MediaAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Column(name = "detail", columnDefinition = "json")
    private String detail;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public MediaAuditEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public void setOrgId(Long v) { this.orgId = v; }
    public void setProjectId(Long v) { this.projectId = v; }
    public void setActorId(String v) { this.actorId = v; }
    public void setActorType(String v) { this.actorType = v; }
    public void setAction(String v) { this.action = v; }
    public void setResourceId(String v) { this.resourceId = v; }
    public void setDetail(String v) { this.detail = v; }
    public void setClientIp(String v) { this.clientIp = v; }
    public void setTraceId(String v) { this.traceId = v; }
    public void setOccurredAt(Instant v) { this.occurredAt = v; }
}
