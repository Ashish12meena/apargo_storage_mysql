package com.aigreentick.services.storage.application.port.in;

import com.aigreentick.services.storage.application.port.in.command.TeardownTenantCommand;

/**
 * Tenant offboarding.
 *
 * <p>Separate from {@code DeleteMediaUseCase} because the operations differ in
 * kind, not just in scale: this one is asynchronous, unbounded in size, requires
 * its own scope, and is triggered by the organisation service rather than by a
 * tenant.
 *
 * <p>The predecessor's equivalents ({@code deleteByOrgAndProject},
 * {@code deleteByOrganisation}) deleted rows, never touched storage, and never
 * released quota — so a torn-down tenant's usage stayed charged forever.
 */
public interface TeardownTenantUseCase {

    /**
     * Accepts the request and returns immediately. An org teardown can span
     * millions of objects and cannot run inside a request.
     *
     * @return a handle for correlating the work in logs and the audit trail
     */
    String requestTeardown(TeardownTenantCommand command);
}
