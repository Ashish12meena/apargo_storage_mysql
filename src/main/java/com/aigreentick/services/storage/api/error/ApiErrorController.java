package com.aigreentick.services.storage.api.error;

import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.common.constants.ApiPaths;
import com.aigreentick.services.storage.common.constants.HeaderNames;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.context.RequestContextData;
import com.aigreentick.services.storage.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Replaces Spring Boot's default {@code /error} body.
 *
 * <p>{@code GlobalExceptionHandler} sees only exceptions inside the dispatcher.
 * Anything the container rejects first — an oversized multipart request above
 * {@code max-request-size}, an exception escaping a filter — is forwarded here,
 * and Boot's default answer is a different JSON shape. This keeps those in the
 * standard wrapper too.
 *
 * <p>Registered filters do not run on the error dispatch, so the request id is
 * recovered from the {@code X-Request-Id} header already set on the response.
 */
@Slf4j
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping(ApiPaths.ERROR)
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request, HttpServletResponse response) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = HttpStatus.resolve(statusAttr instanceof Integer i ? i : 500);
        if (status == null || !status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorCode code = ErrorCode.forStatus(status.value());
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = uri != null ? uri.toString() : request.getRequestURI();

        boolean ownContext = RequestContext.get() == null;
        if (ownContext) {
            RequestContext.set(new RequestContextData(response.getHeader(HeaderNames.REQUEST_ID),
                    null, request.getRemoteAddr(), Instant.now()));
        }
        try {
            log.warn("container-level error {} on {}", status.value(), path);
            String message = status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR.defaultMessage()
                    : code.defaultMessage();
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(status.value(), code.name(), message, List.of(), path));
        } finally {
            if (ownContext) {
                RequestContext.clear();
            }
        }
    }
}
