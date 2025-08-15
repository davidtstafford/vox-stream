package com.voxstream.core.twitch.connection;

import org.springframework.stereotype.Component;

import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;

/**
 * Spring factory exposing TwitchPlatformConnection to the registry.
 */
@Component
public class TwitchPlatformConnectionFactory implements PlatformConnectionFactory {

    private final TwitchPlatformConnection connection;

    public TwitchPlatformConnectionFactory(TwitchPlatformConnection connection) {
        this.connection = connection;
    }

    @Override
    public String platformId() {
        return "twitch";
    }

    @Override
    public PlatformConnection create() {
        return connection;
    }
}
