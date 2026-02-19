package com.aigreentick.services.storage.integration.fallback;

import org.springframework.stereotype.Component;

import com.aigreentick.services.storage.integration.organisation.dto.AccessTokenCredentials;
import com.aigreentick.services.storage.integration.service.interfaces.UserClient;

@Component
public class UserClientFallback implements UserClient {

    @Override
    public AccessTokenCredentials getPhoneNumberIdAccessToken() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPhoneNumberIdAccessToken'");
    }

    @Override
    public AccessTokenCredentials getWabaAccessToken() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getWabaAccessToken'");
    }
    
}
