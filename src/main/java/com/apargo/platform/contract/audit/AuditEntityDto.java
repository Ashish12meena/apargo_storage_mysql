package com.apargo.platform.contract.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The single record the event is about.
 *
 * @param type upper-case entity type, e.g. {@code TEMPLATE}, {@code MEDIA}
 * @param id   the record's id, as text
 * @param name optional display name
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "id", "name"})
public record AuditEntityDto(String type, String id, String name) {

    public static AuditEntityDto of(String type, String id) {
        return new AuditEntityDto(type, id, null);
    }
}
