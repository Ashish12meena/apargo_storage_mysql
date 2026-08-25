package com.aigreentick.services.storage.infrastructure.persistence.repository;

import com.aigreentick.services.storage.infrastructure.persistence.entity.MediaAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only. There is intentionally no update or delete method here, and the
 * application's database role should hold no UPDATE or DELETE grant on the table —
 * a convention that can be violated is not an audit trail.
 */
interface MediaAuditJpaRepository extends JpaRepository<MediaAuditEntity, Long> {
}
