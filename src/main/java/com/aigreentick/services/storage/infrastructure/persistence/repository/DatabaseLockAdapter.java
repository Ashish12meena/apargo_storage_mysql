package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.application.port.out.DistributedLockPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Lease-based lock in {@code scheduler_lock}.
 *
 * <p>Database, not Redis: the lock lives in the same store as the data it guards,
 * so it cannot be lost by an independent failure. Reconciliation runs once a night,
 * so latency is irrelevant and correctness is not (ADR-008).
 *
 * <p>Transactions are opened with an explicit {@link TransactionTemplate} rather
 * than {@code @Transactional}. This is not a style choice. {@link #runIfAcquired}
 * calls {@link #tryAcquire} and {@link #release} on {@code this}, and a self-call
 * never crosses the Spring proxy — so with the annotation the lock statements ran
 * with NO transaction at all, and {@code tryAcquire}'s {@code @Modifying} native
 * query threw {@code TransactionRequiredException} on every pass. Because each
 * job's own try/catch sits inside the lambda, the throw escaped before the task
 * ran: every locked job (session sweep, media purge, quota reconciliation, orphan
 * reclaim, record retention) silently never executed while the service stayed
 * healthy. A template works identically whether called through the proxy or not.
 */
@Repository
@Slf4j
public class DatabaseLockAdapter implements DistributedLockPort {

    private final SchedulerLockJpaRepository jpa;
    private final TransactionTemplate requiresNew;
    private final String owner = resolveOwner();

    public DatabaseLockAdapter(SchedulerLockJpaRepository jpa,
                               PlatformTransactionManager transactionManager) {
        this.jpa = jpa;
        // REQUIRES_NEW: the lock must commit independently of the job's own
        // transaction, or a long job holds an open transaction for its entire
        // duration and the lease is invisible to other replicas until it ends.
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static String resolveOwner() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (UnknownHostException e) {
            return "unknown-" + UUID.randomUUID();
        }
    }

    @Override
    public boolean tryAcquire(String lockName, Duration leaseDuration) {
        return Boolean.TRUE.equals(requiresNew.execute(status -> {
            Instant now = Instant.now();
            int rows = jpa.tryAcquire(lockName, owner, now, now.plus(leaseDuration));

            // MySQL returns 1 for an insert and 2 for an applied update; 0 means
            // the IF conditions preserved the existing values, i.e. a live lease.
            boolean acquired = rows == 1 || rows == 2;
            if (!acquired) {
                log.debug("lock {} already held", lockName);
            }
            return acquired;
        }));
    }

    @Override
    public void release(String lockName) {
        requiresNew.executeWithoutResult(status -> jpa.releaseOwned(lockName, owner));
    }

    @Override
    public boolean runIfAcquired(String lockName, Duration leaseDuration, Runnable task) {
        if (!tryAcquire(lockName, leaseDuration)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            try {
                release(lockName);
            } catch (RuntimeException e) {
                // Swallowed deliberately: a failed release must not mask an
                // exception from the task itself, and the lease expiry already
                // guarantees the lock is reclaimed.
                log.warn("failed to release lock {}; lease will expire. cause={}",
                        lockName, e.toString());
            }
        }
    }
}
