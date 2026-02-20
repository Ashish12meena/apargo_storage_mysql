package com.aigreentick.services.storage.domain;

import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStorageId implements Serializable {

    private Long orgId;
    private Long projectId;
}