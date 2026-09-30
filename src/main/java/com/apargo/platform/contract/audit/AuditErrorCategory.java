package com.apargo.platform.contract.audit;

/** Coarse classification of a FAILURE, stable across services. */
public enum AuditErrorCategory {

    /** The input was invalid or could not be read. */
    VALIDATION,

    /** The caller was not authenticated. */
    AUTHENTICATION,

    /** The caller was authenticated but not allowed. */
    AUTHORIZATION,

    /** A business rule refused the action: not found, conflict, invalid state, limit. */
    BUSINESS,

    /** An upstream dependency failed, timed out or was unavailable. */
    EXTERNAL_SERVICE,

    /** An internal error in the producing service. */
    SYSTEM
}
