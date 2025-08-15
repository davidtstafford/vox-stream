package com.voxstream.platform.api;

import java.util.Set;

/**
 * Factory for creating platform connections. Future implementations (e.g.,
 * Twitch) can be discovered via Spring and selected by platform id.
 */
public interface PlatformConnectionFactory {
    String platformId();

    PlatformConnection create();

    /**
     * Optional metadata describing the platform. Default provides minimal info.
     */
    default PlatformMetadata metadata() {
        return new PlatformMetadata(platformId(), platformId(), "1.0.0", Set.of(Capability.EVENTS));
    }
}
