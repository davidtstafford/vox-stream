package com.voxstream.platform.api.dummy2;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.voxstream.platform.api.Capability;
import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;
import com.voxstream.platform.api.PlatformMetadata;

/**
 * Factory for DummySecondPlatformConnection (always-connected placeholder).
 */
@Component
public class DummySecondPlatformConnectionFactory implements PlatformConnectionFactory {
    @Override
    public String platformId() {
        return DummySecondPlatformConnection.PLATFORM_ID;
    }

    @Override
    public PlatformConnection create() {
        return new DummySecondPlatformConnection();
    }

    @Override
    public PlatformMetadata metadata() {
        return new PlatformMetadata(platformId(), "Dummy Second Platform", "1.0.0", Set.of(Capability.EVENTS));
    }
}
