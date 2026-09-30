package com.apargo.platform.contract.audit;

/** How the action reached the producing service. */
public enum AuditChannel {

    /** A user acting through the web application. */
    WEB,

    /** A service-to-service or public API call without a user. */
    API,

    /** A user acting through the mobile application. */
    MOBILE,

    /** Background work: async processing or a scheduled job. */
    WORKER
}
