package com.apargo.platform.contract.audit;

import com.apargo.platform.contract.identity.ActorType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Who performed the action.
 *
 * @param type           USER, SERVICE or SYSTEM
 * @param id             user id, service name, or job name
 * @param name           optional display name
 * @param impersonatorId optional id of the user acting on behalf of {@code id}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "id", "name", "impersonatorId"})
public record AuditActorDto(ActorType type, String id, String name, String impersonatorId) {

    public static AuditActorDto of(ActorType type, String id) {
        return new AuditActorDto(type, id, null, null);
    }
}
