/**
 * Apargo platform event contract — shared by every service that produces or
 * consumes platform events.
 *
 * <p><b>Copied unchanged into every service.</b> Never edit it per service and
 * never define service-local event DTOs; a change here is a cross-service,
 * versioned change ({@link com.apargo.platform.contract.event.EventSchemaVersion}).
 *
 * <ul>
 *   <li>{@code audit}    — business audit events ({@code AuditEventDto})</li>
 *   <li>{@code access}   — access events (auth / gateway only)</li>
 *   <li>{@code event}    — topics, ids, schema version, environments, categories</li>
 *   <li>{@code identity} — who acts ({@code ActorType})</li>
 * </ul>
 */
package com.apargo.platform.contract;
