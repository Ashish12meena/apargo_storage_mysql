package com.aigreentick.services.storage.integration.fallback;

import org.springframework.stereotype.Component;

import com.aigreentick.services.storage.integration.organisation.dto.StorageInfo;
import com.aigreentick.services.storage.integration.service.interfaces.OrganisationClient;

@Component
public class OrganisationClientFallback implements OrganisationClient {

    @Override
    public StorageInfo getStorageInfo() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getStorageInfo'");
    }
    
}
