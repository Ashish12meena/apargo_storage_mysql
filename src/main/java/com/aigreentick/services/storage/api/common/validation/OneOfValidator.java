package com.aigreentick.services.storage.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

public class OneOfValidator implements ConstraintValidator<OneOf, String> {

    private String[] allowed;
    private boolean ignoreCase;

    @Override
    public void initialize(OneOf annotation) {
        this.allowed = annotation.value();
        this.ignoreCase = annotation.ignoreCase();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return Arrays.stream(allowed).anyMatch(a -> ignoreCase ? a.equalsIgnoreCase(value) : a.equals(value));
    }
}
