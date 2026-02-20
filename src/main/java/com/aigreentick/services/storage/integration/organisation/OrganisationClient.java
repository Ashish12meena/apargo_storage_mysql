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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganisationClient {

    private static final String CB = "organisationCB";

    private final OrganisationClientProperties properties;

    // ── Storage Info ────────────────────────────────────────────────────────

    @Retry(name = CB, fallbackMethod = "storageInfoFallback")
    @CircuitBreaker(name = CB, fallbackMethod = "storageInfoFallback")
    public StorageInfo getStorageInfo(Long orgId, Long projectId) {
        if (!properties.isOutgoingEnabled()) {
            throw new ExternalServiceException("Outgoing organisation client calls are disabled");
        }
        return new StorageInfo(52_428_800L, 1_073_741_824L, 1_021_313_024L);
    }

    // Fallback must match: (Long orgId, Long projectId, Throwable ex)
    @SuppressWarnings("unused")
    private StorageInfo storageInfoFallback(Long orgId, Long projectId, Throwable ex) {
        log.error("OrganisationClient storageInfo fallback: {}", ex.getMessage());
        throw new ExternalServiceException("Organisation Service temporarily unavailable");
    }

    // ── Phone Number Credentials ────────────────────────────────────────────

    @Retry(name = CB, fallbackMethod = "phoneCredentialsFallback")
    @CircuitBreaker(name = CB, fallbackMethod = "phoneCredentialsFallback")
    public AccessTokenCredentials getPhoneNumberCredentials(Long projectId, String wabaId) {
        if (!properties.isOutgoingEnabled()) {
            throw new ExternalServiceException("Outgoing organisation client calls are disabled");
        }
        // TODO: replace with actual call
        return new AccessTokenCredentials(
                "730591696796813",
                "EAAOcfziRygMBPGSZCjTEADbcIXleBDVHuZAF61EDXn6qw2GuS6ghjiVHESlosKbAFGEAGMkArSBqyyyaqUxS51dSiLFtZBRd0oEZAY1LiNElHPcM3bsRzqNjaQZAXht6WOKuEWEGfotJASpCGqMOKBrXUMQr03TopqfrZCBe4xrmlfwVipb6dYQaVkmn8gCqzN");
    }

    // Fallback must match: (Long projectId, String wabaId, Throwable ex)
    @SuppressWarnings("unused")
    private AccessTokenCredentials phoneCredentialsFallback(Long projectId, String wabaId, Throwable ex) {
        log.error("OrganisationClient phoneCredentials fallback: projectId={} wabaId={} cause={}",
                projectId, wabaId, ex.getMessage());
        throw new ExternalServiceException("Organisation Service temporarily unavailable");
    }

    // ── WABA Credentials ────────────────────────────────────────────────────

    @Retry(name = CB, fallbackMethod = "wabaCredentialsFallback")
    @CircuitBreaker(name = CB, fallbackMethod = "wabaCredentialsFallback")
    public AccessTokenCredentials getWabaCredentials(Long orgId) {
        if (!properties.isOutgoingEnabled()) {
            throw new ExternalServiceException("Outgoing organisation client calls are disabled");
        }
        // TODO: replace with actual call
        return new AccessTokenCredentials(
                "530819718510685",
                "EAAOcfziRygMB...");
    }

    // Fallback must match: (Long orgId, Throwable ex)
    @SuppressWarnings("unused")
    private AccessTokenCredentials wabaCredentialsFallback(Long orgId, Throwable ex) {
        log.error("OrganisationClient wabaCredentials fallback: orgId={} cause={}", orgId, ex.getMessage());
        throw new ExternalServiceException("Organisation Service temporarily unavailable");
    }
}