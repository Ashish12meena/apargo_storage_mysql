package com.aigreentick.services.storage.integration.facebook;

import com.aigreentick.services.storage.integration.facebook.dto.*;
import com.aigreentick.services.storage.integration.facebook.properties.FacebookClientProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookMediaClient {

    private static final String CB_MEDIA   = "whatsappMediaCB";
    private static final String CB_UPLOAD  = "facebookUploadCB";
    private static final String RL_MEDIA   = "whatsappMediaRL";
    private static final String RL_UPLOAD  = "facebookUploadRL";
    private static final String RT_MEDIA   = "whatsappMediaRetry";
    private static final String RT_UPLOAD  = "facebookUploadRetry";

    private final WebClient.Builder webClientBuilder;
    private final FacebookClientProperties properties;

    // ── Direct Upload ────────────────────────────────────────────────────────

    @Retry(name = RT_MEDIA, fallbackMethod = "uploadMediaFallback")
    @CircuitBreaker(name = CB_MEDIA, fallbackMethod = "uploadMediaFallback")
    @RateLimiter(name = RL_MEDIA, fallbackMethod = "uploadMediaFallback")
    public FacebookApiResult<WhatsappMediaUploadResponse> uploadMedia(
            File file, String mimeType, String phoneNumberId, String accessToken) {

        if (!properties.isOutgoingEnabled()) {
            return FacebookApiResult.error("Outgoing requests disabled", 503);
        }

        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .pathSegment(properties.getApiVersion(), phoneNumberId, "media")
                .build().toUri();

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("messaging_product", "whatsapp");
        body.part("file", new FileSystemResource(file))
            .header(HttpHeaders.CONTENT_TYPE, mimeType);

        try {
            WhatsappMediaUploadResponse resp = webClientBuilder.build()
                    .post().uri(uri)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                            .flatMap(e -> Mono.error(new RuntimeException("4xx: " + e))))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                            .flatMap(e -> Mono.error(new RuntimeException("5xx: " + e))))
                    .bodyToMono(WhatsappMediaUploadResponse.class)
                    .block();

            log.info("Media uploaded to WhatsApp. phoneNumberId={}", phoneNumberId);
            return FacebookApiResult.success(resp, 200);

        } catch (WebClientResponseException ex) {
            log.error("Failed upload. phoneNumberId={} status={}", phoneNumberId, ex.getStatusCode().value());
            return FacebookApiResult.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Unexpected error uploading to WhatsApp. phoneNumberId={}", phoneNumberId, ex);
            return FacebookApiResult.error(ex.getMessage(), 500);
        }
    }

    // ── Resumable Upload — Step 1: Initiate session ──────────────────────────

    @Retry(name = RT_UPLOAD, fallbackMethod = "resumableFallback")
    @CircuitBreaker(name = CB_UPLOAD, fallbackMethod = "resumableFallback")
    @RateLimiter(name = RL_UPLOAD, fallbackMethod = "resumableFallback")
    public FacebookApiResult<UploadSessionResponse> initiateUploadSession(
            String fileName, long fileSize, String mimeType, String wabaAppId, String accessToken) {

        if (!properties.isOutgoingEnabled()) {
            return FacebookApiResult.error("Outgoing requests disabled", 503);
        }

        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .pathSegment(properties.getApiVersion(), wabaAppId, "uploads")
                .queryParam("file_name", fileName)
                .queryParam("file_length", fileSize)
                .queryParam("file_type", mimeType)
                .queryParam("access_token", accessToken)
                .build().toUri();

        try {
            UploadSessionResponse resp = webClientBuilder.build()
                    .post().uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                            .flatMap(e -> Mono.error(new RuntimeException("4xx: " + e))))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                            .flatMap(e -> Mono.error(new RuntimeException("5xx: " + e))))
                    .bodyToMono(UploadSessionResponse.class)
                    .block();

            log.info("Upload session initiated. sessionId={}", resp != null ? resp.getUploadSessionId() : null);
            return FacebookApiResult.success(resp, 200);

        } catch (WebClientResponseException ex) {
            return FacebookApiResult.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Error initiating upload session for appId={}", wabaAppId, ex);
            return FacebookApiResult.error(ex.getMessage(), 500);
        }
    }

    // ── Resumable Upload — Step 2: Upload chunk ──────────────────────────────

    @Retry(name = RT_UPLOAD, fallbackMethod = "resumableFallback")
    @CircuitBreaker(name = CB_UPLOAD, fallbackMethod = "resumableFallback")
    @RateLimiter(name = RL_UPLOAD, fallbackMethod = "resumableFallback")
    public FacebookApiResult<UploadMediaResponse> uploadResumableChunk(
            String sessionId, File file, String accessToken, String offset) throws IOException {

        if (!file.exists()) {
            return FacebookApiResult.error("File not found: " + file.getAbsolutePath(), 400);
        }

        URI uri = URI.create(properties.getBaseUrl() + "/" + properties.getApiVersion() + "/" + sessionId);

        try {
            UploadMediaResponse resp = webClientBuilder.build()
                    .post().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken.trim())
                    .header("file_offset", offset.trim())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(BodyInserters.fromResource(new FileSystemResource(file)))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                            .flatMap(e -> Mono.error(new RuntimeException("4xx: " + e))))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                            .flatMap(e -> Mono.error(new RuntimeException("5xx: " + e))))
                    .bodyToMono(UploadMediaResponse.class)
                    .block();

            if (resp == null || resp.getFileHandle() == null) {
                throw new IllegalStateException("Upload failed — handle not returned");
            }

            log.info("Chunk uploaded. handle={}", resp.getFileHandle());
            return FacebookApiResult.success(resp, 200);

        } catch (WebClientResponseException ex) {
            return FacebookApiResult.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Error uploading chunk. sessionId={}", sessionId, ex);
            return FacebookApiResult.error(ex.getMessage(), 500);
        }
    }

    // ── Resumable Upload — Step 3: Check offset ───────────────────────────────

    @Retry(name = RT_UPLOAD, fallbackMethod = "resumableFallback")
    @CircuitBreaker(name = CB_UPLOAD, fallbackMethod = "resumableFallback")
    @RateLimiter(name = RL_UPLOAD, fallbackMethod = "resumableFallback")
    public UploadOffsetResponse getUploadOffset(String sessionId, String accessToken) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .pathSegment(properties.getApiVersion(), sessionId)
                .queryParam("access_token", accessToken)
                .build().toUri();

        return webClientBuilder.build()
                .get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(UploadOffsetResponse.class)
                .doOnNext(r -> log.info("File offset: {}", r.getFileOffset()))
                .block();
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    private FacebookApiResult<WhatsappMediaUploadResponse> uploadMediaFallback(
            File file, String mimeType, String phoneNumberId, String accessToken, Throwable ex) {
        log.warn("Fallback: uploadMedia. phoneNumberId={} cause={}", phoneNumberId, ex.getMessage());
        return FacebookApiResult.error("Media upload failed: " + ex.getMessage(), 503);
    }

    private <T> FacebookApiResult<T> resumableFallback(Object p1, Object p2, Object p3, Object p4, Object p5, Throwable ex) {
        log.warn("Fallback: resumable upload. cause={}", ex.getMessage());
        return FacebookApiResult.error("Upload operation failed: " + ex.getMessage(), 503);
    }
}