package com.apargo.platform.contract.event;

/** Family an event belongs to; each family has its own topic and DTO. */
public enum EventCategory {

    /** A business change or an accepted-business failure ({@code AuditEventDto}). */
    AUDIT,

    /** An authentication / access decision ({@code AccessEventDto}); auth and gateway only. */
    ACCESS
}
