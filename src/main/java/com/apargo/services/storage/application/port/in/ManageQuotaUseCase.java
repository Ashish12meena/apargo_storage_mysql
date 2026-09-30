package com.apargo.services.storage.application.port.in;

import com.apargo.services.storage.application.port.in.command.ProvisionQuotaCommand;
import com.apargo.services.storage.application.port.in.result.QuotaView;
import com.apargo.services.storage.domain.shared.TenantRef;

public interface ManageQuotaUseCase {

    /** Idempotent upsert. Called by the organisation service. */
    QuotaView provision(ProvisionQuotaCommand command);

    QuotaView getOrgQuota(long orgId);

    QuotaView getProjectQuota(TenantRef tenant);

    /** The caller's own quota. The predecessor gave tenants no way to see usage. */
    QuotaView getForTenant(TenantRef tenant);
}
