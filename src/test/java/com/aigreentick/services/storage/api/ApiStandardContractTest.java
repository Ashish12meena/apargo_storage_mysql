package com.aigreentick.services.storage.api;

import com.aigreentick.services.storage.api.error.ErrorResponseWriter;
import com.aigreentick.services.storage.api.security.ApiKeyAuthenticator;
import com.aigreentick.services.storage.api.security.MediaAccessGuard;
import com.aigreentick.services.storage.api.v1.media.MediaController;
import com.aigreentick.services.storage.api.v1.media.mapper.MediaDtoMapper;
import com.aigreentick.services.storage.application.port.in.BatchUploadMediaUseCase;
import com.aigreentick.services.storage.application.port.in.DeleteMediaUseCase;
import com.aigreentick.services.storage.application.port.in.QueryMediaUseCase;
import com.aigreentick.services.storage.application.port.in.UploadMediaUseCase;
import com.aigreentick.services.storage.application.port.in.result.BatchUploadView;
import com.aigreentick.services.storage.application.port.in.result.MediaView;
import com.aigreentick.services.storage.application.port.out.RateLimiterPort;
import com.aigreentick.services.storage.application.shared.PageView;
import com.aigreentick.services.storage.config.SecurityConfig;
import com.aigreentick.services.storage.config.properties.ApiProperties;
import com.aigreentick.services.storage.config.properties.CorsProperties;
import com.aigreentick.services.storage.config.properties.RateLimitProperties;
import com.aigreentick.services.storage.config.properties.RequestLoggingProperties;
import com.aigreentick.services.storage.config.properties.SecurityProperties;
import com.aigreentick.services.storage.domain.exception.MediaNotFoundException;
import com.aigreentick.services.storage.domain.exception.QuotaExceededException;
import com.aigreentick.services.storage.domain.exception.RequestInProgressException;
import com.aigreentick.services.storage.domain.quota.QuotaScope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins storage-service's HTTP contract to the company API Standard, with the
 * real filter chain (request id, access log, authentication) in front
 * of the controller. Use cases are mocked.
 */
@WebMvcTest(controllers = MediaController.class)
@Import({SecurityConfig.class, ErrorResponseWriter.class, ApiKeyAuthenticator.class, MediaDtoMapper.class,
        ApiStandardContractTest.Beans.class})
@EnableConfigurationProperties({SecurityProperties.class, RateLimitProperties.class,
        RequestLoggingProperties.class, ApiProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
        "security.api-key-enabled=true",
        "security.clients[0].id=template-service",
        "security.clients[0].key=" + ApiStandardContractTest.KEY,
        "security.clients[0].scopes=media:read,media:write,media:delete",
        "rate-limit.enabled=false",
        "api.idempotency-key-required=true"
})
class ApiStandardContractTest {

    static final String KEY = "test-template-service-key-0123456789abcdef";

    @TestConfiguration
    static class Beans {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired MockMvc mvc;

    @MockitoBean UploadMediaUseCase uploadUseCase;
    @MockitoBean BatchUploadMediaUseCase batchUploadUseCase;
    @MockitoBean QueryMediaUseCase queryUseCase;
    @MockitoBean DeleteMediaUseCase deleteUseCase;
    @MockitoBean MediaAccessGuard guard;
    @MockitoBean RateLimiterPort rateLimiter;
    @MockitoBean PlatformTransactionManager transactionManager;

    private static MediaView media(String id) {
        return new MediaView(id, "a.png", "image/png", 10, "IMAGE", "ACTIVE", null,
                "http://x/serve/" + id, null, Instant.parse("2026-01-15T10:30:00Z"), "template-service");
    }

    private static MockMultipartFile png(String part) {
        return new MockMultipartFile(part, "a.png", "image/png", new byte[]{1, 2, 3});
    }

    // ── wrapper and headers ────────────────────────────────────────────────

    @Test
    void listUsesWrapperCursorPaginationAndEchoesRequestId() throws Exception {
        when(queryUseCase.list(any())).thenReturn(new PageView<>(List.of(media("41")), "c2", true));

        mvc.perform(get("/api/v1/media")
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20")
                        .header("X-Request-Id", "req-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-1"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.pagination.size").value(20))
                .andExpect(jsonPath("$.data.pagination.nextCursor").value("c2"))
                .andExpect(jsonPath("$.data.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.meta.requestId").value("req-1"))
                .andExpect(jsonPath("$.meta.path").doesNotExist())
                .andExpect(header().doesNotExist("X-Trace-Id"));
    }

    @Test
    void sizeOutOfRangeAndUnknownTypeAre422() throws Exception {
        mvc.perform(get("/api/v1/media?size=500")
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));

        mvc.perform(get("/api/v1/media?type=GIF")
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    }

    // ── authentication and tenancy ─────────────────────────────────────────

    @Test
    void missingKeyIs401InWrapper() throws Exception {
        mvc.perform(get("/api/v1/media").header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$", hasKey("data")))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.meta.path").value("/api/v1/media"));
    }

    @Test
    void missingTenantHeaderWithValidKeyIs400() throws Exception {
        mvc.perform(get("/api/v1/media").header("X-Internal-Api-Key", KEY).header("X-Project-Id", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("X-Org-Id")));
    }

    /** Strictly the standard name: the pre-standard X-Api-Key is not an alias. */
    @Test
    void preStandardApiKeyHeaderIsRejected() throws Exception {
        mvc.perform(get("/api/v1/media")
                        .header("X-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void lastPageHasNullCursor() throws Exception {
        when(queryUseCase.list(any())).thenReturn(new PageView<>(List.of(), null, false));

        mvc.perform(get("/api/v1/media")
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.pagination.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.data.pagination.hasNext").value(false));
    }

    // ── status-code table ──────────────────────────────────────────────────

    @Test
    void uploadIs201WithLocation() throws Exception {
        when(uploadUseCase.uploadProxied(any())).thenReturn(media("41"));

        mvc.perform(multipart("/api/v1/media/upload").file(png("file"))
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20")
                        .header("X-Idempotency-Key", "k-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/media/41"))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value("41"));
    }

    @Test
    void uploadWithoutIdempotencyKeyIs400AndDoesNothing() throws Exception {
        mvc.perform(multipart("/api/v1/media/upload").file(png("file"))
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(uploadUseCase, never()).uploadProxied(any());
    }

    /** Strictly the standard name: the pre-standard Idempotency-Key does not count. */
    @Test
    void preStandardIdempotencyKeyHeaderIsNotAccepted() throws Exception {
        mvc.perform(multipart("/api/v1/media/upload").file(png("file"))
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20")
                        .header("Idempotency-Key", "k-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(uploadUseCase, never()).uploadProxied(any());
    }

    /** The exact call template-service makes; the data shape it parses must not move. */
    @Test
    void batchUploadIs200WithUnchangedResultShape() throws Exception {
        when(batchUploadUseCase.uploadBatch(any())).thenReturn(new BatchUploadView(1, 1, List.of(
                BatchUploadView.ItemView.success("a.png", media("41")),
                BatchUploadView.ItemView.failed("b.png", "CONTENT_TYPE_MISMATCH", "The file contents do not match."))));

        mvc.perform(multipart("/api/v1/media/upload/batch").file(png("files")).file(png("files"))
                        .header("X-Internal-Api-Key", KEY).header("X-Internal-Caller", "template-service")
                        .header("X-Org-Id", "10").header("X-Project-Id", "20")
                        .header("X-Idempotency-Key", "batch-1").header("X-Request-Id", "sync-7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.results[0].originalFilename").value("a.png"))
                .andExpect(jsonPath("$.data.results[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.results[0].url").isString())
                .andExpect(jsonPath("$.data.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[1].error.code").value("CONTENT_TYPE_MISMATCH"))
                .andExpect(jsonPath("$.meta.requestId").value("sync-7"));
    }

    @Test
    void deleteIs204WithRequestId() throws Exception {
        mvc.perform(delete("/api/v1/media/41")
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(content().string(""));
    }

    // ── error codes carry their own status ─────────────────────────────────

    @Test
    void notFoundUsesResourceCode() throws Exception {
        when(queryUseCase.getById(any(), any())).thenThrow(new MediaNotFoundException("media 9 at key tenant/10/..."));

        mvc.perform(get("/api/v1/media/9")
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Media not found."));
    }

    @Test
    void quotaExceededKeeps507() throws Exception {
        when(uploadUseCase.uploadProxied(any())).thenThrow(new QuotaExceededException(QuotaScope.PROJECT, 10, 0));

        mvc.perform(multipart("/api/v1/media/upload").file(png("file"))
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20")
                        .header("X-Idempotency-Key", "k-3"))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.status").value(507))
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
    }

    @Test
    void inProgressIs409WithRetryAfter() throws Exception {
        when(uploadUseCase.uploadProxied(any())).thenThrow(new RequestInProgressException("key k-4 in flight"));

        mvc.perform(multipart("/api/v1/media/upload").file(png("file"))
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20")
                        .header("X-Idempotency-Key", "k-4"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_IN_PROGRESS"));
    }

    @Test
    void malformedMediaIdIs400() throws Exception {
        mvc.perform(get("/api/v1/media/not-a-number")
                        .header("X-Internal-Api-Key", KEY).header("X-Org-Id", "10").header("X-Project-Id", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
