package com.aigreentick.services.storage.integration.account;

import com.aigreentick.services.storage.constants.ResilienceConstants;
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

    private final WhatsappAccountClientProperties properties;

    @Retry(name = ResilienceConstants.CB_ORGANISATION, fallbackMethod = "phoneCredentialsFallback")
    @CircuitBreaker(name = ResilienceConstants.CB_ORGANISATION, fallbackMethod = "phoneCredentialsFallback")
    public AccessTokenCredentials getPhoneNumberCredentials(Long projectId, String wabaId) {
        if (!properties.isOutgoingEnabled()) {
            throw new ExternalServiceException("Outgoing organisation client calls are disabled");
        }
        // TODO: replace with actual HTTP call to waba-service
        return new AccessTokenCredentials(
                "730591696796813",
                "EAAOcfziRygMBPGSZCjTEADbcIXleBDVHuZAF61EDXn6qw2GuS6ghjiVHESlosKbAFGEAGMkArSBqyyyaqUxS51dSiLFtZBRd0oEZAY1LiNElHPcM3bsRzqNjaQZAXht6WOKuEWEGfotJASpCGqMOKBrXUMQr03TopqfrZCBe4xrmlfwVipb6dYQaVkmn8gCqzN");
    }

    @SuppressWarnings("unused")
    private AccessTokenCredentials phoneCredentialsFallback(Long projectId, String wabaId, Throwable ex) {
        log.error("OrganisationClient fallback: projectId={} wabaId={} cause={}",
                projectId, wabaId, ex.getMessage());
        throw new ExternalServiceException("Organisation Service temporarily unavailable");
    }
}