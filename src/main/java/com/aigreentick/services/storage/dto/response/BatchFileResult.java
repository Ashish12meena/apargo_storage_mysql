package com.aigreentick.services.storage.dto.response;

import com.aigreentick.services.storage.enums.MediaType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchFileResult {

    public enum Status { SUCCESS, FAILED }

    private String originalFilename;
    private Status status;
    private String url;
    private MediaType mediaType;
    private String contentType;
    private Long fileSizeBytes;
    private String error;

    public static BatchFileResult success(String originalFilename, String url,
                                          MediaType mediaType, String contentType,
                                          Long fileSizeBytes) {
        return BatchFileResult.builder()
                .originalFilename(originalFilename)
                .status(Status.SUCCESS)
                .url(url)
                .mediaType(mediaType)
                .contentType(contentType)
                .fileSizeBytes(fileSizeBytes)
                .build();
    }

    public static BatchFileResult failed(String originalFilename, String error) {
        return BatchFileResult.builder()
                .originalFilename(originalFilename)
                .status(Status.FAILED)
                .error(error)
                .build();
    }
}