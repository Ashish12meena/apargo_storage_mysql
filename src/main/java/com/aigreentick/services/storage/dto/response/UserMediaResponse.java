package com.aigreentick.services.storage.dto.response;

import java.time.Instant;

import com.aigreentick.services.storage.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMediaResponse {
    
    private Long id;
    
    private String url;
    
    private String originalFilename;
    
    private String storedFilename;
    
    private MediaType mediaType;
    
    private String contentType;
    
    private String mediaId;
    
    private Long fileSizeBytes;
    
    private Instant uploadedAt;
    
    private Instant createdAt;
}
