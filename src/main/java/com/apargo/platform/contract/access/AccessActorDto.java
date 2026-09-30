package com.apargo.platform.contract.access;

import com.apargo.platform.contract.identity.ActorType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Who attempted access. {@code id} may be absent on a failed sign-in for an
 * unknown account; {@code name} then carries the submitted identifier (never a
 * password or token).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "id", "name"})
public record AccessActorDto(ActorType type, String id, String name) {
}
