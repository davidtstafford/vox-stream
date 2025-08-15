package com.voxstream.core.twitch.connection;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.voxstream.platform.api.Capability;
import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;
import com.voxstream.platform.api.PlatformMetadata;

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

    @Override
    public PlatformMetadata metadata() {
        return new PlatformMetadata(platformId(), "Twitch",
                connection.getClass().getPackage().getImplementationVersion() == null ? "1.0.0"
                        : connection.getClass().getPackage().getImplementationVersion(),
                Set.of(Capability.EVENTS));
    }
}
