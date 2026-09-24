package com.aigreentick.services.storage.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must be one of a fixed set, e.g. an allowed {@code sort} field or
 * {@code order} direction. Reported as field code {@code INVALID_VALUE}.
 * {@code null} is valid; combine with {@code @NotNull} when required.
 */
@Documented
@Constraint(validatedBy = OneOfValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OneOf {

    String[] value();

    boolean ignoreCase() default false;

    String message() default "must be one of {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
