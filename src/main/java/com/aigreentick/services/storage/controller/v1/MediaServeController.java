package com.aigreentick.services.storage.controller.v1;

import com.aigreentick.services.storage.config.properties.provider.LocalStorageProperties;
import com.aigreentick.services.storage.exception.MediaNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves locally stored media files.
 * Handles URLs like: /api/v1/media/serve/org-1/proj-1/image/{uuid}.png
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media/serve")
@RequiredArgsConstructor
public class MediaServeController {

    private final LocalStorageProperties localStorageProperties;

    /**
     * Streams a file from local storage.
     * The storage key is extracted from the remaining path after /serve/.
     *
     * Example: GET /api/v1/media/serve/org-1/proj-1/image/abc.png
     *          → storageKey = "org-1/proj-1/image/abc.png"
     */
    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        // Extract storage key from the URL path after "/api/v1/media/serve/"
        String fullPath = request.getRequestURI();
        String prefix = "/api/v1/media/serve/";
        String storageKey = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());

        log.debug("Serving file with storageKey: {}", storageKey);

        // Prevent path traversal
        if (storageKey.contains("..")) {
            throw new MediaNotFoundException("Invalid path: " + storageKey);
        }

        Path filePath = Paths.get(localStorageProperties.getRootPath(), storageKey).normalize();

        // Ensure resolved path is still within root
        Path rootPath = Paths.get(localStorageProperties.getRootPath()).normalize();
        if (!filePath.startsWith(rootPath)) {
            throw new MediaNotFoundException("Invalid path: " + storageKey);
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new MediaNotFoundException("File not found: " + storageKey);
        }

        // Determine content type
        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (IOException e) {
            contentType = null;
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        Resource resource = new FileSystemResource(filePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}