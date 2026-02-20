package com.aigreentick.services.storage.schedular;


import com.aigreentick.services.storage.domain.OrgStorage;
import com.aigreentick.services.storage.domain.ProjectStorage;
import com.aigreentick.services.storage.repository.OrgStorageRepository;
import com.aigreentick.services.storage.repository.ProjectStorageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nightly reconciliation job that corrects any drift between
 * quota counters and actual media rows.
 *
 * 1. For each project: recalculate used_bytes from SUM(media.file_size)
 * 2. For each org: recalculate used_bytes from SUM(project_storage.used_bytes)
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quota.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class QuotaReconciliationService {

    private final OrgStorageRepository orgStorageRepo;
    private final ProjectStorageRepository projectStorageRepo;

    @Scheduled(cron = "${quota.reconciliation.cron:0 0 3 * * ?}")  // 3 AM daily
    @Transactional
    public void reconcile() {
        log.info("Starting quota reconciliation job");

        AtomicInteger projectFixed = new AtomicInteger(0);
        AtomicInteger orgFixed = new AtomicInteger(0);

        // Step 1: Fix project-level counters
        List<ProjectStorage> allProjects = projectStorageRepo.findAll();
        for (ProjectStorage proj : allProjects) {
            long actualBytes = projectStorageRepo.sumActiveMediaBytes(proj.getOrgId(), proj.getProjectId());

            if (proj.getUsedBytes() != actualBytes) {
                log.warn("Project drift detected: org={} project={} recorded={} actual={}",
                        proj.getOrgId(), proj.getProjectId(), proj.getUsedBytes(), actualBytes);
                proj.setUsedBytes(actualBytes);
                projectStorageRepo.save(proj);
                projectFixed.incrementAndGet();
            }
        }

        // Step 2: Fix org-level counters (derived from project totals)
        List<OrgStorage> allOrgs = orgStorageRepo.findAll();
        for (OrgStorage org : allOrgs) {
            long actualBytes = orgStorageRepo.sumProjectUsedBytes(org.getOrgId());

            if (org.getUsedBytes() != actualBytes) {
                log.warn("Org drift detected: org={} recorded={} actual={}",
                        org.getOrgId(), org.getUsedBytes(), actualBytes);
                org.setUsedBytes(actualBytes);
                orgStorageRepo.save(org);
                orgFixed.incrementAndGet();
            }
        }

        log.info("Quota reconciliation complete. Projects fixed: {}, Orgs fixed: {}",
                projectFixed.get(), orgFixed.get());
    }
}