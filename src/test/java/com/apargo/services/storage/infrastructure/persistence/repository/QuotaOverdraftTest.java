package com.apargo.services.storage.infrastructure.persistence.repository;

import com.apargo.services.storage.domain.shared.ByteSize;
import com.apargo.services.storage.domain.shared.TenantRef;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code X-Quota-Policy: overdraft}: the bytes are counted at both scopes and nothing is
 * refused — the limit-checking statements are never issued.
 */
class QuotaOverdraftTest {

    private final OrgStorageJpaRepository orgs = mock(OrgStorageJpaRepository.class);
    private final ProjectStorageJpaRepository projects = mock(ProjectStorageJpaRepository.class);
    private final QuotaRepositoryAdapter adapter = new QuotaRepositoryAdapter(orgs, projects);

    private final TenantRef tenant = new TenantRef(5L, 438L);

    @Test
    void countsAtBothScopesWithoutTheLimitCheck() {
        when(projects.reserveOverdraft(5L, 438L, 2048L)).thenReturn(1);
        when(orgs.reserveOverdraft(5L, 2048L)).thenReturn(1);

        adapter.reserveOverdraft(tenant, ByteSize.of(2048));

        verify(projects).reserveOverdraft(5L, 438L, 2048L);
        verify(orgs).reserveOverdraft(5L, 2048L);
        verify(projects, never()).reserve(anyLong(), anyLong(), anyLong());
        verify(orgs, never()).reserve(anyLong(), anyLong());
    }

    @Test
    void aTenantWithNoQuotaRowIsNotRefused() {
        when(projects.reserveOverdraft(5L, 438L, 2048L)).thenReturn(0);
        when(orgs.reserveOverdraft(5L, 2048L)).thenReturn(0);

        // No exception: an unprovisioned tenant's customer file is still stored.
        adapter.reserveOverdraft(tenant, ByteSize.of(2048));

        verify(orgs).reserveOverdraft(5L, 2048L);
    }
}
