package com.aigreentick.services.storage.integration.organisation.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccessTokenCredentials {
    private final String id; // WABA ID or PhoneNumber ID
    private final String accessToken;
}
