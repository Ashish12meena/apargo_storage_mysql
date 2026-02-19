package com.aigreentick.services.storage.validator;

import com.aigreentick.services.storage.config.properties.PaginationProperties;
import com.aigreentick.services.storage.context.UserContext;
import com.aigreentick.services.storage.exception.MediaValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaRequestValidator {

    private final PaginationProperties paginationProperties;

    public Pageable validateAndBuildPageable(Integer page, Integer size) {
        page = page != null ? page : 0;
        size = size != null ? size : paginationProperties.getDefaultPageSize();

        if (page < 0) {
            throw new MediaValidationException("Page number must be >= 0");
        }
        if (size < paginationProperties.getMinPageSize()) {
            throw new MediaValidationException("Page size must be > " + paginationProperties.getMinPageSize());
        }
        if (size > paginationProperties.getMaxPageSize()) {
            size = paginationProperties.getMaxPageSize();
        }

        return PageRequest.of(page, size);
    }

    public void validateUserContext() {
        Long orgId = UserContext.getOrganisationId();
        Long projectId = UserContext.getProjectId();

        if (orgId == null || orgId <= 0) {
            log.error("Invalid organisation context - orgId: {}", orgId);
            throw new MediaValidationException("Organisation context is invalid or missing");
        }
        if (projectId == null || projectId <= 0) {
            log.error("Invalid project context - projectId: {}", projectId);
            throw new MediaValidationException("Project context is invalid or missing");
        }
    }

    public void validateFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new MediaValidationException("Filename cannot be empty");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new MediaValidationException("Invalid filename format");
        }
    }
}