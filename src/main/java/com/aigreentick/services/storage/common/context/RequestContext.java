package com.aigreentick.services.storage.common.context;

/**
 * ThreadLocal holder for {@link RequestContextData}, valid on the request thread
 * only. Anything asynchronous receives context as an explicit parameter — a
 * ThreadLocal read from a pool thread is either empty or another request's
 * leftovers.
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContextData> HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(RequestContextData data) {
        HOLDER.set(data);
    }

    public static RequestContextData get() {
        return HOLDER.get();
    }

    /** The request's {@code X-Request-Id}, or null outside a request. */
    public static String requestIdOrNull() {
        RequestContextData data = HOLDER.get();
        return data == null ? null : data.requestId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
