package com.voxstream.platform.api.dummy;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.voxstream.platform.api.Capability;
import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;
import com.voxstream.platform.api.PlatformMetadata;

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

    @Override
    public PlatformMetadata metadata() {
        return new PlatformMetadata(platformId(), "Dummy Platform", "1.0.0", Set.of(Capability.EVENTS));
    }
}
