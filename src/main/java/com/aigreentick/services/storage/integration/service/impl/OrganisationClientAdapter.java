package com.aigreentick.services.storage.integration.service.impl;

import org.springframework.stereotype.Component;

// import com.aigreentick.services.storage.client.service.interfaces.OrganisationClient;
import com.aigreentick.services.storage.exception.ExternalServiceException;
import com.aigreentick.services.storage.integration.organisation.dto.StorageInfo;
import com.aigreentick.services.storage.integration.properties.OrganisationClientProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganisationClientAdapter {
    private static final String ORG_CLIENT_CB = "organisationClientCircuitBreaker";

    // private final OrganisationClient orgClient;
    private final OrganisationClientProperties properties;

    @Retry(name = ORG_CLIENT_CB, fallbackMethod = "getStorageInfoFallback")
    @CircuitBreaker(name = ORG_CLIENT_CB, fallbackMethod = "getStorageInfoFallback")
    public StorageInfo getStorageInfo() {
        if (!properties.isOutgoingEnabled()) {
            log.warn("Outgoing call to Organisation Service is disabled via configuration");
            throw new ExternalServiceException("Outgoing organisation client calls are currently disabled.");
        }

        log.debug("Calling Organisation Service to fetch storage info...");

        // StorageInfo info = orgClient.getStorageInfo();

        // if (info == null) {
        //     log.error("Received null StorageInfo response from Organisation Service.");
        //     throw new ExternalServiceException("Organisation Service returned an invalid response (null StorageInfo).");
        // }

        // log.info("Successfully fetched storage info: {}", info);
        // return info;
        return new StorageInfo(52428800, 1073741824, 1021313024);
    }

    public StorageInfo getStorageInfoFallback(Throwable throwable) {
        log.error("Fallback triggered for OrganisationClientAdapter.getStorageInfo: {}", throwable.getMessage(),
                throwable);

        throw new ExternalServiceException(
                "Organisation Service is temporarily unavailable. Please try again later.");
    }

}
