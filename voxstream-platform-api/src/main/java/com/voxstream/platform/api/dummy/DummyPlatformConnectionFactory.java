package com.voxstream.platform.api.dummy;

import org.springframework.stereotype.Component;

import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;

/**
 * Spring-discoverable factory for DummyPlatformConnection.
 */
@Component
public class DummyPlatformConnectionFactory implements PlatformConnectionFactory {
    @Override
    public String platformId() {
        return DummyPlatformConnection.PLATFORM_ID;
    }

    @Override
    public PlatformConnection create() {
        return new DummyPlatformConnection();
    }
}
