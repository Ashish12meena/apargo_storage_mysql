package com.aigreentick.services.storage.api.security;

import com.aigreentick.services.storage.common.constants.HeaderNames;
import com.aigreentick.services.storage.config.properties.ApiProperties;
import com.aigreentick.services.storage.domain.exception.IdempotencyKeyRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * Rejects a {@link RequiresIdempotencyKey} call without a usable
 * {@code X-Idempotency-Key} before any work (or quota reservation) happens.
 *
 * <p>Presence and shape only. Replay, in-progress and reuse detection belong to
 * {@code IdempotencyGuard}, which stores the key per tenant. Only the standard
 * name is read; the pre-standard {@code Idempotency-Key} is not accepted.
 *
 * <p>An exception thrown here goes through {@code GlobalExceptionHandler}, so the
 * 400 arrives in the standard wrapper.
 */
@Component
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final ApiProperties properties;

    public IdempotencyKeyInterceptor(ApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(RequiresIdempotencyKey.class)) {
            return true;
        }
        String key = request.getHeader(HeaderNames.IDEMPOTENCY_KEY);
        if (key == null || key.isBlank()) {
            if (properties.idempotencyKeyRequired()) {
                throw new IdempotencyKeyRequiredException("missing " + HeaderNames.IDEMPOTENCY_KEY
                        + " on " + request.getRequestURI());
            }
            return true;
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IdempotencyKeyRequiredException("malformed " + HeaderNames.IDEMPOTENCY_KEY
                    + " on " + request.getRequestURI());
        }
        return true;
    }
}
