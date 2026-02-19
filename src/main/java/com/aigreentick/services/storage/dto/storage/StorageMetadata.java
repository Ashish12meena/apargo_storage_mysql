package com.aigreentick.services.storage.dto.storage;

import com.aigreentick.services.storage.enums.MediaType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageMetadata {
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Long organisationId;
    private Long projectId;
    private MediaType mediaType;
    private String fileExtension;

    /**
     * Generate storage key: org-{orgId}/proj-{projectId}/{mediaType}/{uuid}.{ext}
     * This structure allows easy bulk deletion by project or by org.
     */
    public String generateStorageKey() {
        String uuid = java.util.UUID.randomUUID().toString();
        return String.format("org-%d/proj-%d/%s/%s%s",
                organisationId,
                projectId,
                mediaType.name().toLowerCase(),
                uuid,
                fileExtension != null ? fileExtension : ""
        );
    }
}