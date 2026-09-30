package com.apargo.services.storage.application.port.out;

import com.apargo.services.storage.domain.shared.TenantRef;
import com.apargo.services.storage.domain.upload.UploadSession;
import com.apargo.services.storage.domain.upload.UploadSessionId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadSessionPort {

    UploadSession save(UploadSession session);

    Optional<UploadSession> findByIdForTenant(UploadSessionId id, TenantRef tenant);

    /** Sweeper input. Bounded batch, so one pass cannot monopolise a connection. */
    List<UploadSession> findReclaimableForMaintenance(Instant now, int limit);
}
