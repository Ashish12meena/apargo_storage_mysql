package com.aigreentick.services.storage.dto.response;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
public class BatchValidationResult {
    private List<MultipartFile> validFiles;
    private List<BatchFileResult> rejectedResults;
    private long totalValidSize;
}