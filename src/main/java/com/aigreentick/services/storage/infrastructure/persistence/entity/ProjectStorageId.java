package com.aigreentick.services.storage.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@code project_storage}. */
public class ProjectStorageId implements Serializable {

    private Long orgId;
    private Long projectId;

    public ProjectStorageId() {
    }

    public ProjectStorageId(Long orgId, Long projectId) {
        this.orgId = orgId;
        this.projectId = projectId;
    }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long v) { this.orgId = v; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectStorageId other)) {
            return false;
        }
        return Objects.equals(orgId, other.orgId) && Objects.equals(projectId, other.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, projectId);
    }
}
