package com.aigreentick.services.storage.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "project_storage")
@IdClass(ProjectStorageId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStorage {

    @Id
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Id
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "max_bytes", nullable = false)
    private Long maxBytes;

    @Column(name = "used_bytes", nullable = false)
    @Builder.Default
    private Long usedBytes = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Helper methods ──────────────────────────────────────────────────

    public long getRemainingBytes() {
        return maxBytes - usedBytes;
    }

    public boolean hasCapacity(long fileSize) {
        return (usedBytes + fileSize) <= maxBytes;
    }

    public void incrementUsage(long fileSize) {
        this.usedBytes += fileSize;
        this.updatedAt = Instant.now();
    }

    public void decrementUsage(long fileSize) {
        this.usedBytes = Math.max(0, this.usedBytes - fileSize);
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.usedBytes == null) this.usedBytes = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}