package com.aigreentick.services.storage.integration.account;

import com.aigreentick.services.storage.exception.ExternalServiceException;
import com.aigreentick.services.storage.integration.account.dto.AccessTokenCredentials;
import com.aigreentick.services.storage.integration.account.properties.WhatsappAccountClientProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsappAccountClient {

    private static final String CB = "organisationCB";

    private final WhatsappAccountClientProperties properties;


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

}