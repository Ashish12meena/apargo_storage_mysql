package com.aigreentick.services.storage.service.impl.quota;



import com.aigreentick.services.storage.domain.OrgStorage;
import com.aigreentick.services.storage.domain.ProjectStorage;
import com.aigreentick.services.storage.exception.MediaValidationException;
import com.aigreentick.services.storage.exception.StorageLimitExceededException;
import com.aigreentick.services.storage.repository.OrgStorageRepository;
import com.aigreentick.services.storage.repository.ProjectStorageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles all quota enforcement within a single DB transaction.
 *
 * Lock ordering: project_storage FIRST, then org_storage — always.
 * This prevents deadlocks when concurrent uploads target different
 * projects under the same organisation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final ProjectStorageRepository projectStorageRepo;
    private final OrgStorageRepository orgStorageRepo;

    // ── Check + Reserve ─────────────────────────────────────────────────────

    /**
     * Acquires locks, validates capacity at both levels, and increments
     * used_bytes. Must be called inside the same transaction as the media
     * insert so everything rolls back together on failure.
     */
    @Transactional(propagation = Propagation.MANDATORY)  // must join caller's tx
    public void reserveQuota(Long orgId, Long projectId, long fileSize) {
        // 1. Lock project row first
        ProjectStorage project = projectStorageRepo.findByIdForUpdate(orgId, projectId)
                .orElseThrow(() -> new MediaValidationException(
                        String.format("Storage quota not provisioned for org=%d project=%d. " +
                                      "Ask your admin to provision quota first.", orgId, projectId)));

        if (!project.hasCapacity(fileSize)) {
            throw new StorageLimitExceededException(
                    String.format("Project storage quota exceeded. Available: %d bytes, required: %d bytes",
                            project.getRemainingBytes(), fileSize));
        }

        // 2. Lock org row second (consistent ordering)
        OrgStorage org = orgStorageRepo.findByIdForUpdate(orgId)
                .orElseThrow(() -> new MediaValidationException(
                        String.format("Organisation storage quota not provisioned for org=%d", orgId)));

        if (!org.hasCapacity(fileSize)) {
            throw new StorageLimitExceededException(
                    String.format("Organisation storage quota exceeded. Available: %d bytes, required: %d bytes",
                            org.getRemainingBytes(), fileSize));
        }

        // 3. Increment both
        project.incrementUsage(fileSize);
        org.incrementUsage(fileSize);

        projectStorageRepo.save(project);
        orgStorageRepo.save(org);

        log.debug("Quota reserved: org={} project={} fileSize={} | orgUsed={}/{} projUsed={}/{}",
                orgId, projectId, fileSize,
                org.getUsedBytes(), org.getMaxBytes(),
                project.getUsedBytes(), project.getMaxBytes());
    }

    // ── Release ─────────────────────────────────────────────────────────────

    /**
     * Decrements used_bytes at both levels. Called during media deletion
     * inside the same transaction as the media row delete/soft-delete.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseQuota(Long orgId, Long projectId, long fileSize) {
        // Same lock ordering: project first, org second
        projectStorageRepo.findByIdForUpdate(orgId, projectId)
                .ifPresent(p -> {
                    p.decrementUsage(fileSize);
                    projectStorageRepo.save(p);
                });

        orgStorageRepo.findByIdForUpdate(orgId)
                .ifPresent(o -> {
                    o.decrementUsage(fileSize);
                    orgStorageRepo.save(o);
                });

        log.debug("Quota released: org={} project={} fileSize={}", orgId, projectId, fileSize);
    }

    // ── Provisioning (called by internal API) ───────────────────────────────

    /**
     * Create or update org-level quota limit. Idempotent upsert.
     */
    @Transactional
    public OrgStorage upsertOrgQuota(Long orgId, long maxBytes) {
        OrgStorage org = orgStorageRepo.findById(orgId).orElse(null);

        if (org == null) {
            org = OrgStorage.builder()
                    .orgId(orgId)
                    .maxBytes(maxBytes)
                    .usedBytes(0L)
                    .build();
            log.info("Creating org quota: orgId={} maxBytes={}", orgId, maxBytes);
        } else {
            org.setMaxBytes(maxBytes);
            org.setUpdatedAt(Instant.now());
            log.info("Updating org quota: orgId={} maxBytes={}", orgId, maxBytes);
        }

        return orgStorageRepo.save(org);
    }

    /**
     * Create or update project-level quota limit. Idempotent upsert.
     * Requires the org quota row to already exist (FK constraint).
     */
    @Transactional
    public ProjectStorage upsertProjectQuota(Long orgId, Long projectId, long maxBytes) {
        // Ensure org row exists
        if (!orgStorageRepo.existsById(orgId)) {
            throw new MediaValidationException(
                    "Cannot provision project quota: org quota for org=" + orgId + " does not exist. Provision org quota first.");
        }

        ProjectStorage project = projectStorageRepo
                .findById(new com.aigreentick.services.storage.domain.ProjectStorageId(orgId, projectId))
                .orElse(null);

        if (project == null) {
            project = ProjectStorage.builder()
                    .orgId(orgId)
                    .projectId(projectId)
                    .maxBytes(maxBytes)
                    .usedBytes(0L)
                    .build();
            log.info("Creating project quota: orgId={} projectId={} maxBytes={}", orgId, projectId, maxBytes);
        } else {
            project.setMaxBytes(maxBytes);
            project.setUpdatedAt(Instant.now());
            log.info("Updating project quota: orgId={} projectId={} maxBytes={}", orgId, projectId, maxBytes);
        }

        return projectStorageRepo.save(project);
    }

    // ── Read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrgStorage getOrgQuota(Long orgId) {
        return orgStorageRepo.findById(orgId).orElse(null);
    }

    @Transactional(readOnly = true)
    public ProjectStorage getProjectQuota(Long orgId, Long projectId) {
        return projectStorageRepo
                .findById(new com.aigreentick.services.storage.domain.ProjectStorageId(orgId, projectId))
                .orElse(null);
    }
}