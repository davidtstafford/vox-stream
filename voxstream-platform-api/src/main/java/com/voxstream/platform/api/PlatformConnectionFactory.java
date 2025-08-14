package com.voxstream.platform.api;

/**
 * Factory for creating platform connections. Future implementations (e.g.,
 * Twitch)
 * can be discovered via Spring and selected by platform id.
 */
public interface PlatformConnectionFactory {
    String platformId();

    PlatformConnection create();
}
