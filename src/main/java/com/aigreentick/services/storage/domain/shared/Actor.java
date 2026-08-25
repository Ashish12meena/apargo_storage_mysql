package com.aigreentick.services.storage.domain.shared;

/** WHO performed an operation. Tenant is <em>where</em>; actor is <em>who</em>. */
public record Actor(String userId, ActorType type, String requestIp) {

    public enum ActorType {
        USER,
        SERVICE,
        SYSTEM
    }

    public static Actor system(String jobName) {
        return new Actor(jobName, ActorType.SYSTEM, null);
    }

    public Long userIdAsLong() {
        if (userId == null) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
