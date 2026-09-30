package com.apargo.platform.contract.identity;

/** Kind of principal that performed an action. */
public enum ActorType {

    /** A human user, identified by the user id. */
    USER,

    /** Another service calling on its own behalf, identified by the service name. */
    SERVICE,

    /** The platform itself (scheduled jobs, maintenance), identified by the job name. */
    SYSTEM
}
