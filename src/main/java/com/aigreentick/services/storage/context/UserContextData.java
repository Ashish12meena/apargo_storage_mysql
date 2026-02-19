package com.aigreentick.services.storage.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserContextData {
    private final Long organisationId;
    private final Long projectId;
}