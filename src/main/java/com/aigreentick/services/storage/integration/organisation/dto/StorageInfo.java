package com.aigreentick.services.storage.integration.organisation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorageInfo {
    private long storageUsed; // total bytes used
    private long maxStorage; // max allowed bytes
    private long remaining; // derived = quota - used
}
