package com.aigreentick.services.storage.integration.organisation;

import com.aigreentick.services.storage.exception.ExternalServiceException;
import com.aigreentick.services.storage.integration.organisation.dto.AccessTokenCredentials;
import com.aigreentick.services.storage.integration.organisation.dto.StorageInfo;
import com.aigreentick.services.storage.integration.organisation.properties.OrganisationClientProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for the Organisation Service.
 * Stub implementations return hardcoded values until Feign client is wired.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganisationClient {

    private static final String CB = "organisationCB";

    private final OrganisationClientProperties properties;

    @Retry(name = CB, fallbackMethod = "storageInfoFallback")
    @CircuitBreaker(name = CB, fallbackMethod = "storageInfoFallback")
    public StorageInfo getStorageInfo(Long orgId, Long projectId) {
        if (!properties.isOutgoingEnabled()) {
            throw new ExternalServiceException("Outgoing organisation client calls are disabled");
        }
    
        return new StorageInfo(52_428_800L, 1_073_741_824L, 1_021_313_024L);
    }

    @SuppressWarnings("unused")
    private StorageInfo storageInfoFallback(Throwable ex) {
        log.error("OrganisationClient fallback triggered: {}", ex.getMessage());
        throw new ExternalServiceException("Organisation Service temporarily unavailable");
    }

    @Retry(name = CB, fallbackMethod = "credentialsFallback")
    @CircuitBreaker(name = CB, fallbackMethod = "credentialsFallback")
    public AccessTokenCredentials getPhoneNumberCredentials(Long projectId, String wabaId) {
        if (!properties.isOutgoingEnabled()) {
            throw new ExternalServiceException("Outgoing organisation client calls are disabled");
        }
        // TODO: replace with actual call
        return new AccessTokenCredentials(
                "730591696796813",
                "EAAOcfziRygMBPGSZCjTEADbcIXleBDVHuZAF61EDXn6qw2GuS6ghjiVHESlosKbAFGEAGMkArSBqyyyaqUxS51dSiLFtZBRd0oEZAY1LiNElHPcM3bsRzqNjaQZAXht6WOKuEWEGfotJASpCGqMOKBrXUMQr03TopqfrZCBe4xrmlfwVipb6dYQaVkmn8gCqzN");
    }

    @Retry(name = CB, fallbackMethod = "credentialsFallback")
    @CircuitBreaker(name = CB, fallbackMethod = "credentialsFallback")
    public AccessTokenCredentials getWabaCredentials(Long orgId) {
        if (!properties.isOutgoingEnabled()) {
            throw new ExternalServiceException("Outgoing organisation client calls are disabled");
        }
        // TODO: replace with actual call
        return new AccessTokenCredentials(
                "530819718510685",
                "EAAOcfziRygMB...");
    }

    private AccessTokenCredentials credentialsFallback(Long orgId, Throwable ex) {
        log.error("OrganisationClient credential fallback. orgId={} cause={}", orgId, ex.getMessage());
        throw new ExternalServiceException("Organisation Service temporarily unavailable");
    }
}
