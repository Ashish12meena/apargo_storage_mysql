package com.aigreentick.services.storage.repository;

public interface MediaRepositoryCustom {
    int softDeleteById(Long mediaId, Long deletedBy);
}