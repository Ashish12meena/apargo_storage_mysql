package com.aigreentick.services.storage.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a create operation that must carry {@code X-Idempotency-Key}
 * (API Standard §1). Enforced by {@link IdempotencyKeyInterceptor}; the replay
 * itself is done by {@code IdempotencyGuard} in the application layer.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresIdempotencyKey {
}
