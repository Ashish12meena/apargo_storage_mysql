package com.aigreentick.services.storage.dto.file;
import java.io.File;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileDetailsDto {
    private File file;
    private String fileName;
    private String mimeType;
    private long fileSize;
}
