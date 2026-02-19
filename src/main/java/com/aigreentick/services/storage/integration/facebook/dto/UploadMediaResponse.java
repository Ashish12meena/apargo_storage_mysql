package com.aigreentick.services.storage.integration.facebook.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UploadMediaResponse {
    @JsonProperty("h")
    private String facebookImageUrl;
}