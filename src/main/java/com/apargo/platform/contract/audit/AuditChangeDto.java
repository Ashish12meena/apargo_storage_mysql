package com.apargo.platform.contract.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One changed field. Values are scalars (text, number, boolean, enum name).
 *
 * <p>{@code null} values are written explicitly: "was unset" and "is now unset"
 * are information.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"field", "oldValue", "newValue"})
public record AuditChangeDto(String field, Object oldValue, Object newValue) {
}
