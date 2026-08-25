package com.aigreentick.services.storage.api.security;

import com.aigreentick.services.storage.domain.exception.TenantAccessDeniedException;

/**
 * ThreadLocal holder for the verified principal, valid on the request thread only.
 *
 * <p>Anything asynchronous receives the principal as an explicit PARAMETER. A
 * ThreadLocal read from a pool thread is either empty or another request's
 * leftovers — the failure that forced the predecessor to maintain two parallel
 * upload implementations.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantPrincipal> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantPrincipal principal) {
        HOLDER.set(principal);
    }

    public static TenantPrincipal getOrNull() {
        return HOLDER.get();
    }

    public static TenantPrincipal require() {
        TenantPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new TenantAccessDeniedException("no verified principal on this request");
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
