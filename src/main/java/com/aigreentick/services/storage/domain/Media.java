package com.aigreentick.services.storage.domain;

import java.time.Instant;

import com.aigreentick.services.storage.enums.MediaStatus;
import com.aigreentick.services.storage.enums.MediaType;
import com.aigreentick.services.storage.enums.StorageProviderType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "media",
    indexes = {
        @Index(name = "idx_media_org_project",               columnList = "organisation_id, project_id"),
        @Index(name = "idx_media_org_project_type",          columnList = "organisation_id, project_id, media_type"),
        @Index(name = "idx_media_org_project_created",       columnList = "organisation_id, project_id, created_at DESC"),
        @Index(name = "idx_media_org_project_type_created",  columnList = "organisation_id, project_id, media_type, created_at DESC"),
        @Index(name = "idx_media_media_id",                  columnList = "media_id"),
        @Index(name = "idx_media_stored_filename",           columnList = "stored_filename"),
        @Index(name = "idx_media_status",                    columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── File Identity ────────────────────────────────────────────────────────

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    // /** SHA-256 hex digest — used for duplicate detection */
    // @Column(name = "checksum", nullable = false, length = 64)
    // private String checksum;

    // ── Media Classification ─────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 50)
    private MediaType mediaType;

    // ── Storage Location ─────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 50)
    private StorageProviderType storageProvider;

    /** Provider-specific key: org-{id}/proj-{id}/{type}/{uuid}.{ext} */
    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Column(name = "storage_bucket")
    private String storageBucket;

    @Column(name = "storage_region", length = 100)
    private String storageRegion;

    @Column(name = "media_url", length = 2048)
    private String mediaUrl;

    // ── WhatsApp / Facebook ──────────────────────────────────────────────────

    /** WhatsApp media_id returned after upload to Graph API */
    @Column(name = "media_id")
    private String mediaId;

    @Column(name = "waba_id")
    private String wabaId;

    // ── Tenant Context ───────────────────────────────────────────────────────

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private MediaStatus status = MediaStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}