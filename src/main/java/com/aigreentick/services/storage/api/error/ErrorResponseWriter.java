package com.aigreentick.services.storage.api.error;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.api.common.dto.response.ErrorBody;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the standard envelope from OUTSIDE the Spring dispatcher.
 *
 * <p>Servlet filters reject requests before {@code @RestControllerAdvice} can see
 * them. Without a shared writer each filter hand-rolls its own JSON, which is how
 * error shapes drift apart.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, ErrorCode code, String traceId)
            throws IOException {
        write(response, status, code, code.defaultMessage(), traceId);
    }

    public void write(HttpServletResponse response, int status, ErrorCode code, String message, String traceId)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(ErrorBody.of(code.name(), message), traceId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
