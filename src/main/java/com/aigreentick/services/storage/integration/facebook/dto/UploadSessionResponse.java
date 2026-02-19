package com.aigreentick.services.storage.integration.facebook.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UploadSessionResponse {
    @JsonProperty("id")
    private String uploadSessionId;
}
