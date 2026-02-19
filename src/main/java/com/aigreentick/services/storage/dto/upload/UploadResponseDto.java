package com.aigreentick.services.storage.dto.upload;

import lombok.Data;

@Data
public class UploadResponseDto {
    private Long id;
    private String url;
    private String fileName;
    private String contentType;
    private long size;

    // constructors, getters, setters
}
