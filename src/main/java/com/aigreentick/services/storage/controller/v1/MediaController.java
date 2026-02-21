package com.aigreentick.services.storage.controller.v1;

import com.aigreentick.services.storage.context.UserContext;
import com.aigreentick.services.storage.dto.response.ApiResponse;
import com.aigreentick.services.storage.dto.response.MediaUploadResponse;
import com.aigreentick.services.storage.dto.response.UserMediaResponse;
import com.aigreentick.services.storage.service.impl.media.ConcurrentMediaUploadService;
import com.aigreentick.services.storage.service.impl.media.MediaUploadOrchestrator;
import com.aigreentick.services.storage.validator.MediaRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Upload and retrieve media files")
public class MediaController {

    private final ConcurrentMediaUploadService concurrentUploadService;
    private final MediaUploadOrchestrator orchestrator;
    private final MediaRequestValidator validator;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a media file")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Waba-Id") String wabaId) {

        // Extract context HERE on HTTP thread — ThreadLocal still works
        Long orgId = UserContext.getOrganisationId();
        Long projectId = UserContext.getProjectId();

        validator.validateUserContext();

        log.info("Upload request: file={} org={} project={}", file.getOriginalFilename(), orgId, projectId);

        // Pass context explicitly — no ThreadLocal dependency downstream
        MediaUploadResponse response = concurrentUploadService
                .uploadMediaSync(file, wabaId, orgId, projectId);

        return ResponseEntity.ok(ApiResponse.success("Media uploaded successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all media (paginated)")
    public ResponseEntity<ApiResponse<Page<UserMediaResponse>>> getAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        validator.validateUserContext();
        Pageable pageable = validator.validateAndBuildPageable(page, size);
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getMedia(pageable)));
    }

    @GetMapping("/images")
    @Operation(summary = "Get images")
    public ResponseEntity<ApiResponse<Page<UserMediaResponse>>> getImages(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        validator.validateUserContext();
        return ResponseEntity.ok(ApiResponse.success(
                orchestrator.getMediaByType(com.aigreentick.services.storage.enums.MediaType.IMAGE,
                        validator.validateAndBuildPageable(page, size))));
    }

    @GetMapping("/videos")
    @Operation(summary = "Get videos")
    public ResponseEntity<ApiResponse<Page<UserMediaResponse>>> getVideos(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        validator.validateUserContext();
        return ResponseEntity.ok(ApiResponse.success(
                orchestrator.getMediaByType(com.aigreentick.services.storage.enums.MediaType.VIDEO,
                        validator.validateAndBuildPageable(page, size))));
    }

    @GetMapping("/documents")
    @Operation(summary = "Get documents")
    public ResponseEntity<ApiResponse<Page<UserMediaResponse>>> getDocuments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        validator.validateUserContext();
        return ResponseEntity.ok(ApiResponse.success(
                orchestrator.getMediaByType(com.aigreentick.services.storage.enums.MediaType.DOCUMENT,
                        validator.validateAndBuildPageable(page, size))));
    }

    @GetMapping("/audio")
    @Operation(summary = "Get audio files")
    public ResponseEntity<ApiResponse<Page<UserMediaResponse>>> getAudio(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        validator.validateUserContext();
        return ResponseEntity.ok(ApiResponse.success(
                orchestrator.getMediaByType(com.aigreentick.services.storage.enums.MediaType.AUDIO,
                        validator.validateAndBuildPageable(page, size))));
    }

    @GetMapping("/public-url")
    @Operation(summary = "Get a public/pre-signed URL for a storage key")
    public ResponseEntity<ApiResponse<String>> getPublicUrl(
            @RequestParam @NotBlank String storageKey,
            @RequestParam(required = false, defaultValue = "3600") Long durationSeconds) {

        validator.validateUserContext();
        String url = orchestrator.getPublicUrl(storageKey, Duration.ofSeconds(durationSeconds));
        return ResponseEntity.ok(ApiResponse.success("Public URL generated", url));
    }
}