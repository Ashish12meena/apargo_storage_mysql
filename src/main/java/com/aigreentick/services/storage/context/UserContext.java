package com.aigreentick.services.storage.context;

public class UserContext {

    private static final ThreadLocal<UserContextData> CONTEXT = new ThreadLocal<>();

    private UserContext() {}

    public static void set(UserContextData data) {
        CONTEXT.set(data);
    }

    public static UserContextData get() {
        return CONTEXT.get();
    }

    public static Long getOrganisationId() {
        UserContextData data = CONTEXT.get();
        return data != null ? data.getOrganisationId() : null;
    }

    public static Long getProjectId() {
        UserContextData data = CONTEXT.get();
        return data != null ? data.getProjectId() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}