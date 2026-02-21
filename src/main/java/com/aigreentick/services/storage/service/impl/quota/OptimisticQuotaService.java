package com.aigreentick.services.storage.service.impl.quota;

import com.aigreentick.services.storage.domain.OrgStorage;
import com.aigreentick.services.storage.domain.ProjectStorage;
import com.aigreentick.services.storage.exception.MediaValidationException;
import com.aigreentick.services.storage.exception.StorageLimitExceededException;
import com.aigreentick.services.storage.repository.OrgStorageRepository;
import com.aigreentick.services.storage.repository.ProjectStorageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optimistic-locking quota service for high-concurrency uploads.
 *
 * Uses @Version on entities + @Retryable to handle conflicts.
 * Each retry runs in a FRESH transaction (REQUIRES_NEW) so the
 * stale entity is re-read from DB with the latest version.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimisticQuotaService {

    private final ProjectStorageRepository projectStorageRepo;
    private final OrgStorageRepository orgStorageRepo;

    /**
     * Reserve quota using optimistic locking.
     * On version conflict, Spring Retry retries with exponential backoff + jitter.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 2, random = true)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveQuota(Long orgId, Long projectId, long fileSize) {
        // 1. Read project row (plain SELECT — no lock)
        ProjectStorage project = projectStorageRepo.findByOrgAndProject(orgId, projectId)
                .orElseThrow(() -> new MediaValidationException(
                        String.format("Storage quota not provisioned for org=%d project=%d. " +
                                      "Ask your admin to provision quota first.", orgId, projectId)));

        if (!project.hasCapacity(fileSize)) {
            throw new StorageLimitExceededException(
                    String.format("Project storage quota exceeded. Available: %d bytes, required: %d bytes",
                            project.getRemainingBytes(), fileSize));
        }

        // 2. Read org row (plain SELECT — no lock)
        OrgStorage org = orgStorageRepo.findByOrgId(orgId)
                .orElseThrow(() -> new MediaValidationException(
                        String.format("Organisation storage quota not provisioned for org=%d", orgId)));

        if (!org.hasCapacity(fileSize)) {
            throw new StorageLimitExceededException(
                    String.format("Organisation storage quota exceeded. Available: %d bytes, required: %d bytes",
                            org.getRemainingBytes(), fileSize));
        }

        // 3. Increment — on flush, @Version triggers optimistic lock check.
        //    If another thread committed first, ObjectOptimisticLockingFailureException
        //    is thrown and @Retryable re-reads + retries.
        project.incrementUsage(fileSize);
        org.incrementUsage(fileSize);

        projectStorageRepo.save(project);
        orgStorageRepo.save(org);

        log.debug("Quota reserved (optimistic): org={} project={} fileSize={} | orgUsed={}/{} projUsed={}/{}",
                orgId, projectId, fileSize,
                org.getUsedBytes(), org.getMaxBytes(),
                project.getUsedBytes(), project.getMaxBytes());
    }

    /**
     * Release quota — also uses optimistic locking with retry.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 2, random = true)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseQuota(Long orgId, Long projectId, long fileSize) {
        projectStorageRepo.findByOrgAndProject(orgId, projectId)
                .ifPresent(p -> {
                    p.decrementUsage(fileSize);
                    projectStorageRepo.save(p);
                });

        orgStorageRepo.findByOrgId(orgId)
                .ifPresent(o -> {
                    o.decrementUsage(fileSize);
                    orgStorageRepo.save(o);
                });

        log.debug("Quota released (optimistic): org={} project={} fileSize={}", orgId, projectId, fileSize);
    }
}