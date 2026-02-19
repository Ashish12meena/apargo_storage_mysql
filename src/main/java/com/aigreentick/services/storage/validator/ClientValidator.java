package com.aigreentick.services.storage.validator;

import org.springframework.stereotype.Component;

import com.aigreentick.services.storage.exception.StorageLimitExceededException;
import com.aigreentick.services.storage.integration.adapter.OrganisationClientAdapter;
import com.aigreentick.services.storage.integration.organisation.dto.StorageInfo;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Component
public class ClientValidator {
    private final OrganisationClientAdapter userClient;

    public void validateStorageInfo(long fileSize) {
        StorageInfo storageInfo = userClient.getStorageInfo();
        if (storageInfo.getRemaining() < fileSize) {
            throw new StorageLimitExceededException("Storage of organisation full only remaining "+ storageInfo.getRemaining());
        }

    }
}
