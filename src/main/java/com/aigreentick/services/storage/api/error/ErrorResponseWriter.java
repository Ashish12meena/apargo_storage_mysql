package com.aigreentick.services.storage.api.error;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes the standard error wrapper from OUTSIDE the Spring dispatcher.
 *
 * <p>Servlet filters reject requests before {@code @RestControllerAdvice} can see
 * them. Without a shared writer each filter hand-rolls its own JSON, which is how
 * error shapes drift apart. The HTTP status comes from the {@link ErrorCode}, so
 * a filter cannot pair a code with the wrong status.
 *
 * <p>Clears only the BODY buffer, never the headers. It used to call
 * {@code response.reset()}, which also wiped headers set just before —
 * {@code Retry-After} and {@code X-RateLimit-*} on a 429, and
 * {@code X-Request-Id} on every rejection.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
            throws IOException {
        write(request, response, code, code.defaultMessage());
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(code.httpStatus(), code.name(), message,
                List.of(), request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
