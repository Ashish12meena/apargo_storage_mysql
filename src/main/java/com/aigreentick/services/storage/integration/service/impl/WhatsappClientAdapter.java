package com.aigreentick.services.storage.integration.service.impl;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.aigreentick.services.storage.integration.dto.response.FacebookApiResponse;
import com.aigreentick.services.storage.integration.dto.response.WhatsappMediaUploadResponseDto;
import com.aigreentick.services.storage.integration.facebook.dto.UploadMediaResponse;
import com.aigreentick.services.storage.integration.facebook.dto.UploadOffsetResponse;
import com.aigreentick.services.storage.integration.facebook.dto.UploadSessionResponse;
import com.aigreentick.services.storage.integration.properties.WhatsappClientProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class WhatsappClientAdapter {

    private final WebClient.Builder webClientBuilder;
    private final WhatsappClientProperties properties;

    /**
     * Uploads media to WhatsApp.
     * Wrapped with Retry, CircuitBreaker, and RateLimiter for resilience.
     */
    @Retry(name = "whatsappMediaRetry", fallbackMethod = "uploadMediaFallback")
    @CircuitBreaker(name = "whatsappMediaCircuitBreaker", fallbackMethod = "uploadMediaFallback")
    @RateLimiter(name = "whatsappMediaRateLimiter", fallbackMethod = "rateLimiterFallback")
    public FacebookApiResponse<WhatsappMediaUploadResponseDto> uploadMediaToFacebook(
            File file,
            String mimeType,
            String phoneNumberId,
            String accessToken) {

        if (!properties.isOutgoingEnabled()) {
            return FacebookApiResponse.error("Outgoing requests disabled", 503);
        }

        URI uri = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment(properties.getApiVersion(), phoneNumberId, "media")
                .build()
                .toUri();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("messaging_product", "whatsapp");
        builder.part("file", new FileSystemResource(file))
                .header(HttpHeaders.CONTENT_TYPE, mimeType);

        try {
            WhatsappMediaUploadResponseDto response = webClientBuilder.build()
                    .post()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Facebook API 4xx error for phoneNumberId={}: {}", phoneNumberId, errorBody);
                                return Mono.error(new RuntimeException("Facebook API returned 4xx: " + errorBody));
                            }))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Facebook API 5xx error for phoneNumberId={}: {}", phoneNumberId, errorBody);
                                return Mono.error(new RuntimeException("Facebook API returned 5xx: " + errorBody));
                            }))
                    .bodyToMono(WhatsappMediaUploadResponseDto.class)
                    .block();

            log.info("Media uploaded to WhatsApp. phoneNumberId={} Response={}", phoneNumberId, response);
            return FacebookApiResponse.success(response, 200);

        } catch (WebClientResponseException ex) {
            log.error("Failed to upload media. phoneNumberId={} Status={} Response={}",
                    phoneNumberId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

        } catch (Exception ex) {
            log.error("Unexpected error while uploading media to WhatsApp. phoneNumberId={}", phoneNumberId, ex);
            return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
        }
    }

    // Resumbale api's

    /**
     * Step 1: Initiates an upload session with the Facebook Graph API.
     * Wrapped with Retry, CircuitBreaker, and RateLimiter for resilience.
     */
    @Retry(name = "facebookUploadRetry", fallbackMethod = "uploadFallback")
    @CircuitBreaker(name = "facebookUploadCircuitBreaker", fallbackMethod = "uploadFallback")
    @RateLimiter(name = "facebookUploadRateLimiter", fallbackMethod = "rateLimiterFallback")
    public FacebookApiResponse<UploadSessionResponse> initiateUploadSession(String fileName, long fileSize,
            String mimeType,
            String wabaAppId, String accessToken) {
        if (!properties.isOutgoingEnabled()) {
            return FacebookApiResponse.error("Outgoing requests disabled", 503);
        }

        URI uri = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment(properties.getApiVersion(), wabaAppId, "uploads")
                .queryParam("file_name", fileName)
                .queryParam("file_length", fileSize)
                .queryParam("file_type", mimeType)
                .queryParam("access_token", accessToken)
                .build()
                .toUri();

        log.info("Initiating upload session: {}", uri);

        try {
            UploadSessionResponse response = webClientBuilder.build()
                    .post()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Facebook API 4xx during upload initiation for appId={}: {}", wabaAppId,
                                        errorBody);
                                return Mono.error(new RuntimeException("Facebook API returned 4xx: " + errorBody));
                            }))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Facebook API 5xx during upload initiation for appId={}: {}", wabaAppId,
                                        errorBody);
                                return Mono.error(new RuntimeException("Facebook API returned 5xx: " + errorBody));
                            }))
                    .bodyToMono(UploadSessionResponse.class)
                    .block();

            log.info("Upload session initiated successfully. Session ID: {}", response.getUploadSessionId());
            return FacebookApiResponse.success(response, 200);

        } catch (WebClientResponseException ex) {
            log.error("Upload session initiation failed. AppId={} Status={} Response={}",
                    wabaAppId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

        } catch (Exception ex) {
            log.error("Unexpected error initiating upload session for AppId={}", wabaAppId, ex);
            return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
        }
    }

    /**
     * Step 2: Uploads media to Facebook using an upload session ID.
     * Wrapped with Retry, CircuitBreaker, and RateLimiter for resilience.
     */
    @Retry(name = "facebookUploadRetry", fallbackMethod = "uploadFallback")
    @CircuitBreaker(name = "facebookUploadCircuitBreaker", fallbackMethod = "uploadFallback")
    @RateLimiter(name = "facebookUploadRateLimiter", fallbackMethod = "rateLimiterFallback")
    public FacebookApiResponse<UploadMediaResponse> uploadResumableMediaToFacebook(
            String sessionId,
            File file,
            String accessToken,
            String offset) throws IOException {

        if (!file.exists()) {
            return FacebookApiResponse.error("File not found: " + file.getAbsolutePath(), 400);
        }

        URI uri = URI.create(properties.getBaseUrl() + "/" + properties.getApiVersion() + "/" + sessionId);
        log.info("Uploading media chunk to Facebook: {}", uri);

        try {
            FileSystemResource fileResource = new FileSystemResource(file);

            UploadMediaResponse response = webClientBuilder.build()
                    .post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken.trim())
                    .header("file_offset", String.valueOf(offset).trim())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(BodyInserters.fromResource(fileResource))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Facebook API 4xx during media upload for sessionId={}: {}", sessionId,
                                        errorBody);
                                return Mono.error(new RuntimeException("Facebook API returned 4xx: " + errorBody));
                            }))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Facebook API 5xx during media upload for sessionId={}: {}", sessionId,
                                        errorBody);
                                return Mono.error(new RuntimeException("Facebook API returned 5xx: " + errorBody));
                            }))
                    .bodyToMono(UploadMediaResponse.class)
                    .block();

            if (response == null || response.getFacebookImageUrl() == null) {
                throw new IllegalStateException("Upload failed or handle not returned");
            }

            log.info("Media uploaded successfully. Handle={}", response.getFacebookImageUrl());
            return FacebookApiResponse.success(response, 200);

        } catch (WebClientResponseException ex) {
            log.error("Failed to upload media. SessionId={} Status={} Response={}",
                    sessionId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

        } catch (Exception ex) {
            log.error("Unexpected error during media upload. SessionId={}", sessionId, ex);
            return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
        }
    }

    /**
     * Step 3: Retrieves current file offset for an ongoing upload session.
     * Wrapped with Retry, CircuitBreaker, and RateLimiter for resilience.
     */
    @Retry(name = "facebookUploadRetry", fallbackMethod = "uploadFallback")
    @CircuitBreaker(name = "facebookUploadCircuitBreaker", fallbackMethod = "uploadFallback")
    @RateLimiter(name = "facebookUploadRateLimiter", fallbackMethod = "rateLimiterFallback")
    /**
     * Gets the current file offset for an ongoing Facebook upload session.
     * Used to resume chunked uploads or verify completion.
     *
     * @param sessionId   The upload session ID (e.g., "upload:123456")
     * @param accessToken Valid user access token with upload permission
     * @param apiVersion  Graph API version (e.g., "v23.0")
     * @return UploadOffsetResponse with current file offset
     */
    public UploadOffsetResponse getUploadOffset(String sessionId,
            String accessToken) {

          URI uri = UriComponentsBuilder
            .fromUriString(properties.getBaseUrl())
            .pathSegment(properties.getApiVersion(), sessionId)
            .queryParam("access_token", accessToken)
            .build()
            .toUri();

        log.info("Checking upload offset: {}", uri);

       return webClientBuilder.build()
            .get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(UploadOffsetResponse.class)
            .doOnNext(resp -> log.info("Received file_offset: {}", resp.getFileOffset()))
            .block();
    }

    /**
     * Common fallback for all Facebook upload operations.
     */
    @SuppressWarnings("unused")
    private <T> FacebookApiResponse<T> uploadFallback(
            String param1, Object... args) {

        Throwable ex = args != null && args.length > 0 && args[args.length - 1] instanceof Throwable
                ? (Throwable) args[args.length - 1]
                : new RuntimeException("Unknown failure");

        log.warn("Fallback triggered for Facebook upload operation. Params={} Cause={}", param1, ex.getMessage());
        return FacebookApiResponse.error("Facebook upload operation failed: " + ex.getMessage(), 503);
    }

    /**
     * Fallback method for resilience.
     */
    @SuppressWarnings("unused")
    private FacebookApiResponse<WhatsappMediaUploadResponseDto> uploadMediaFallback(
            File file,
            String mimeType,
            String phoneNumberId,
            String accessToken,
            Throwable ex) {

        log.warn("Fallback triggered for uploadMediaToWhatsapp. phoneNumberId={}", phoneNumberId, ex);
        return FacebookApiResponse.error("Media upload failed due to: " + ex.getMessage(), 503);
    }

    @SuppressWarnings("unused")
    private <T> FacebookApiResponse<T> rateLimiterFallback(
            File file, String mimeType, String phoneNumberId, String accessToken, Throwable ex) {
        log.warn("Rate limiter fallback triggered for WhatsApp upload. Cause={}", ex.getMessage());
        return FacebookApiResponse.error("Rate limit exceeded. Please try again later.", 429);
    }

}
