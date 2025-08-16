package com.voxstream.core.twitch.connection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.twitch.eventsub.EventSubClient;
import com.voxstream.core.twitch.eventsub.EventSubWebSocketClient;
import com.voxstream.core.twitch.eventsub.TwitchEventMapper;
import com.voxstream.core.twitch.model.TwitchOAuthToken;
import com.voxstream.core.twitch.oauth.TwitchOAuthService;
import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformStatus;
import com.voxstream.platform.api.events.PlatformEvent;

/**
 * Twitch platform connection integrating OAuth + EventSub websocket (MVP).
 * This is no longer a Spring component; it is constructed lazily by
 * {@link TwitchPlatformConnectionFactory} so that the platform connection
 * lifecycle is fully owned by the registry/manager layer.
 */
public class TwitchPlatformConnection implements PlatformConnection {
    private static final Logger log = LoggerFactory.getLogger(TwitchPlatformConnection.class);

    private final ConfigurationService config;
    private final TwitchOAuthService oauthService;

    private final List<Consumer<PlatformStatus>> statusListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlatformEvent>> eventListeners = new CopyOnWriteArrayList<>();

    private volatile PlatformStatus status = PlatformStatus.disconnected();
    private volatile boolean connecting;
    private volatile EventSubClient eventSubClient;

    // Primary constructor used by factory (creates default websocket client)
    public TwitchPlatformConnection(ConfigurationService config, TwitchOAuthService oauthService) {
        this(config, oauthService, new EventSubWebSocketClient());
    }

    // Additional constructor for test injection (allows mock EventSubClient)
    public TwitchPlatformConnection(ConfigurationService config, TwitchOAuthService oauthService,
            EventSubClient eventSubClient) {
        this.config = Objects.requireNonNull(config);
        this.oauthService = Objects.requireNonNull(oauthService);
        this.eventSubClient = Objects.requireNonNull(eventSubClient);
        this.oauthService.addTokenListener(t -> {
            // Future enhancement: if EventSub session requires re-auth, handle here
        });
    }

    @Override
    public String platformId() {
        return "twitch";
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        boolean enabled = config.get(CoreConfigKeys.TWITCH_ENABLED)
                || config.getDynamicBoolean("platform.twitch.enabled", false);
        if (!enabled) {
            log.info("Twitch connect requested but platform disabled (both flags false)");
            updateStatus(PlatformStatus.failed("twitch.disabled", true));
            return CompletableFuture.completedFuture(false);
        }
        // Ensure OAuth service started (may have been skipped if user only toggled
        // dynamic flag after app launch)
        try {
            oauthService.start();
        } catch (Exception e) {
            log.debug("OAuth service start attempt failed/ignored: {}", e.getMessage());
        }
        if (status.state() == PlatformStatus.State.CONNECTED) {
            return CompletableFuture.completedFuture(true);
        }
        if (connecting) {
            log.debug("Twitch connect called while already connecting");
            return CompletableFuture.completedFuture(false);
        }
        connecting = true;
        updateStatus(PlatformStatus.connecting());
        log.info("TwitchPlatformConnection starting interactive connect (PKCE/OAuth if needed)...");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var tokOpt = oauthService.ensureTokenInteractive();
                if (tokOpt.isEmpty()) {
                    log.warn("Twitch OAuth token acquisition required (user interaction) - awaiting browser flow");
                    updateStatus(PlatformStatus.failed("oauth.required"));
                    return false;
                }
                TwitchOAuthToken tok = tokOpt.get();
                if (tok.isExpired()) {
                    log.warn("Obtained Twitch token is expired -> failing connect");
                    updateStatus(PlatformStatus.failed("oauth.expired"));
                    return false;
                }
                int hbSec = config.get(CoreConfigKeys.TWITCH_EVENTSUB_HEARTBEAT_INTERVAL_SEC);
                EventSubClient client = eventSubClient;
                if (client == null) {
                    client = new EventSubWebSocketClient();
                    eventSubClient = client;
                }
                client.addHeartbeatTimeoutListener(() -> {
                    log.warn("Twitch EventSub heartbeat timeout -> disconnect & mark failed");
                    safeCloseClient();
                    updateStatus(PlatformStatus.failed("eventsub.heartbeat.timeout"));
                    if (config.get(CoreConfigKeys.TWITCH_ENABLED)
                            || config.getDynamicBoolean("platform.twitch.enabled", false)) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            if (status.state() == PlatformStatus.State.FAILED) {
                                log.info("Attempting reconnect after heartbeat timeout");
                                connect();
                            }
                        });
                    }
                });
                client.addCloseListener(() -> log.info("Twitch EventSub websocket closed"));
                client.addMessageListener(msg -> {
                    try {
                        var evt = TwitchEventMapper.map(msg);
                        if (evt != null) {
                            for (var l : eventListeners) {
                                try {
                                    l.accept(evt);
                                } catch (Exception ignore) {
                                }
                            }
                        }
                        if ("session_welcome".equalsIgnoreCase(msg.metadataType())) {
                            log.info("EventSub session welcome received");
                        }
                    } catch (Exception e) {
                        log.debug("EventSub mapping error: {}", e.toString());
                    }
                });
                String url = "wss://eventsub.wss.twitch.tv/ws";
                log.info("Connecting Twitch EventSub websocket -> {} (hb={}s)", url, hbSec);
                client.connect(url, hbSec).join();
                updateStatus(PlatformStatus.connected(Instant.now().toEpochMilli()));
                log.info("TwitchPlatformConnection CONNECTED (EventSub WS established)");
                return true;
            } catch (Exception e) {
                log.warn("Twitch connect error: {}", e.toString());
                updateStatus(PlatformStatus.failed("exception:" + e.getClass().getSimpleName()));
                safeCloseClient();
                return false;
            } finally {
                connecting = false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        safeCloseClient();
        status = PlatformStatus.disconnected();
        notifyStatus();
        return CompletableFuture.completedFuture(null);
    }

    private void safeCloseClient() {
        EventSubClient c = eventSubClient;
        // Do not null out the client so that an injected/mock client can be reused on
        // reconnect.
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public PlatformStatus status() {
        return status;
    }

    @Override
    public void addStatusListener(Consumer<PlatformStatus> listener) {
        if (listener != null)
            statusListeners.add(listener);
    }

    @Override
    public void addPlatformEventListener(Consumer<PlatformEvent> listener) {
        if (listener != null)
            eventListeners.add(listener);
    }

    @Override
    public boolean isConnected() {
        return status.state() == PlatformStatus.State.CONNECTED;
    }

    private void updateStatus(PlatformStatus newStatus) {
        status = newStatus;
        notifyStatus();
    }

    private void notifyStatus() {
        for (var l : statusListeners) {
            try {
                l.accept(status);
            } catch (Exception ignored) {
            }
        }
    }
}
