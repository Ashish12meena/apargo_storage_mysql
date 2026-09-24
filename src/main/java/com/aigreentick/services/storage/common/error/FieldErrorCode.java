package com.aigreentick.services.storage.common.error;

import java.util.Map;

/**
 * Field-level codes for {@code errors[].code} (API Standard §6).
 *
 * <p>Bean Validation reports the constraint that failed ({@code NotBlank},
 * {@code Size}, ...). {@link #fromConstraint(String)} translates that into the
 * standard vocabulary so the frontend never sees Java annotation names.

 */
public enum FieldErrorCode {
    REQUIRED,
    INVALID_FORMAT,
    INVALID_VALUE,
    TOO_LONG,
    OUT_OF_RANGE,
    DUPLICATE,
    UNKNOWN_FIELD;

    private static final Map<String, FieldErrorCode> BY_CONSTRAINT = Map.ofEntries(
            Map.entry("NotNull", REQUIRED),
            Map.entry("NotBlank", REQUIRED),
            Map.entry("NotEmpty", REQUIRED),
            Map.entry("Size", TOO_LONG),
            Map.entry("Length", TOO_LONG),
            Map.entry("Min", OUT_OF_RANGE),
            Map.entry("Max", OUT_OF_RANGE),
            Map.entry("DecimalMin", OUT_OF_RANGE),
            Map.entry("DecimalMax", OUT_OF_RANGE),
            Map.entry("Positive", OUT_OF_RANGE),
            Map.entry("PositiveOrZero", OUT_OF_RANGE),
            Map.entry("Negative", OUT_OF_RANGE),
            Map.entry("NegativeOrZero", OUT_OF_RANGE),
            Map.entry("Range", OUT_OF_RANGE),
            Map.entry("Digits", OUT_OF_RANGE),
            Map.entry("Pattern", INVALID_FORMAT),
            Map.entry("Email", INVALID_FORMAT),
            Map.entry("URL", INVALID_FORMAT),
            Map.entry("Past", INVALID_VALUE),
            Map.entry("Future", INVALID_VALUE),
            Map.entry("OneOf", INVALID_VALUE),
            Map.entry("typeMismatch", INVALID_VALUE));

    /**
     * @param constraint simple constraint name as reported by Spring's
     *                   {@code FieldError#getCode()} (e.g. {@code "NotBlank"})
     * @return the matching code, {@link #INVALID_VALUE} when unknown
     */
    public static FieldErrorCode fromConstraint(String constraint) {
        if (constraint == null) {
            return INVALID_VALUE;
        }
        return BY_CONSTRAINT.getOrDefault(constraint, INVALID_VALUE);
    }

    /**
     * Spring reports codes most-specific-first ({@code "Min.list.size"},
     * {@code "Min.int"}, {@code "Min"}); the last entry is the bare
     * constraint name.
     */
    public static FieldErrorCode fromCodes(String[] codes) {
        if (codes == null || codes.length == 0) {
            return INVALID_VALUE;
        }
        return fromConstraint(codes[codes.length - 1]);
    }
}
