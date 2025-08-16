package com.voxstream.core.twitch.connection;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.twitch.oauth.TwitchOAuthService;
import com.voxstream.platform.api.Capability;
import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;
import com.voxstream.platform.api.PlatformMetadata;

/**
 * Spring factory exposing TwitchPlatformConnection to the registry.
 * The underlying connection is now a lazily constructed singleton inside this
 * factory.
 */
@Component
public class TwitchPlatformConnectionFactory implements PlatformConnectionFactory {

    private final ConfigurationService config;
    private final TwitchOAuthService oauthService;
    private volatile TwitchPlatformConnection singleton; // lazy init

    public TwitchPlatformConnectionFactory(ConfigurationService config, TwitchOAuthService oauthService) {
        this.config = config;
        this.oauthService = oauthService;
    }

    @Override
    public String platformId() {
        return "twitch";
    }

    @Override
    public PlatformConnection create() {
        TwitchPlatformConnection inst = singleton;
        if (inst == null) {
            synchronized (this) {
                inst = singleton;
                if (inst == null) {
                    inst = new TwitchPlatformConnection(config, oauthService);
                    singleton = inst;
                }
            }
        }
        return inst;
    }

    @Override
    public PlatformMetadata metadata() {
        return new PlatformMetadata(platformId(), "Twitch",
                TwitchPlatformConnection.class.getPackage().getImplementationVersion() == null ? "1.0.0"
                        : TwitchPlatformConnection.class.getPackage().getImplementationVersion(),
                Set.of(Capability.EVENTS));
    }
}
