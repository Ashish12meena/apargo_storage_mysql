package com.aigreentick.services.storage.integration.service.interfaces;

import org.springframework.web.bind.annotation.GetMapping;

import com.aigreentick.services.storage.integration.organisation.dto.StorageInfo;



public interface OrganisationClient {
    @GetMapping("/storage-info")
    StorageInfo getStorageInfo();
}
