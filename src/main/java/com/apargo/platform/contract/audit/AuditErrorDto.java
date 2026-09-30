package com.apargo.platform.contract.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Why a FAILURE event failed. Present only when {@code status = FAILURE}.
 *
 * @param category  coarse classification
 * @param code      the producing service's error code
 * @param message   kept only for BUSINESS / VALIDATION; generic otherwise
 * @param details   small scalar map; never stack traces or upstream bodies
 * @param reference the trace id, so the failure can be found in the logs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"category", "code", "message", "details", "reference"})
public record AuditErrorDto(AuditErrorCategory category, String code, String message,
                            Map<String, Object> details, String reference) {

    public AuditErrorDto {
        if (details != null) {
            Map<String, Object> copy = new LinkedHashMap<>();
            details.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
            details = copy.isEmpty() ? null : Collections.unmodifiableMap(copy);
        }
    }
}
