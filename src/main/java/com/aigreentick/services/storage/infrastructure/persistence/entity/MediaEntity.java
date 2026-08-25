package com.aigreentick.services.storage.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Maps {@code media}.
 *
 * <p>Persistence shape only, deliberately separate from {@code domain.media.Media}.
 * A JPA entity needs a no-arg constructor and mutable fields, which is exactly what
 * makes it unable to enforce an invariant; keeping them apart is what lets the
 * lifecycle state machine actually reject illegal transitions.
 */
@Entity
@Table(name = "media",
        uniqueConstraints = @UniqueConstraint(name = "uq_media_storage_key",
                columnNames = "storage_key"),
        indexes = {
                // Keyset pagination. created_at alone is not unique, so id is the
                // tiebreaker that gives the cursor a total order.
                @Index(name = "idx_media_keyset",
                        columnList = "organisation_id, project_id, created_at DESC, id DESC"),
                @Index(name = "idx_media_keyset_type",
                        columnList = "organisation_id, project_id, media_type, created_at DESC, id DESC"),
                @Index(name = "idx_media_purge_after", columnList = "status, purge_after"),
                @Index(name = "idx_media_status_created", columnList = "status, created_at"),
                @Index(name = "idx_media_upload_session", columnList = "upload_session_id")
        })
public class MediaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", length = 255)
    private String storedFilename;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    /** @deprecated a persisted presigned URL is stale when written. Dropped in Phase 4. */
    @Deprecated
    @Column(name = "media_url", length = 2048)
    private String mediaUrl;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "detected_mime_type", length = 100)
    private String detectedMimeType;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "storage_provider", nullable = false, length = 20)
    private String storageProvider;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "scan_status", nullable = false, length = 20)
    private String scanStatus;

    @Column(name = "upload_session_id", length = 36)
    private String uploadSessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    /** WHEN policy allows removal. deleted_at records WHEN the delete happened. */
    @Column(name = "purge_after")
    private Instant purgeAfter;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MediaEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganisationId() { return organisationId; }
    public void setOrganisationId(Long v) { this.organisationId = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String v) { this.originalFilename = v; }
    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String v) { this.storedFilename = v; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String v) { this.storageKey = v; }
    @Deprecated public String getMediaUrl() { return mediaUrl; }
    @Deprecated public void setMediaUrl(String v) { this.mediaUrl = v; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String v) { this.mimeType = v; }
    public String getDetectedMimeType() { return detectedMimeType; }
    public void setDetectedMimeType(String v) { this.detectedMimeType = v; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String v) { this.mediaType = v; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long v) { this.fileSize = v; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String v) { this.checksumSha256 = v; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String v) { this.storageProvider = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getScanStatus() { return scanStatus; }
    public void setScanStatus(String v) { this.scanStatus = v; }
    public String getUploadSessionId() { return uploadSessionId; }
    public void setUploadSessionId(String v) { this.uploadSessionId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long v) { this.deletedBy = v; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public void setPurgeAfter(Instant v) { this.purgeAfter = v; }
    public Long getVersion() { return version; }
    public void setVersion(Long v) { this.version = v; }
}
