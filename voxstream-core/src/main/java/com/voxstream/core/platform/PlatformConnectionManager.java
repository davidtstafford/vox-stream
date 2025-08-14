package com.voxstream.core.platform;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.voxstream.core.bus.EventBus;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.event.BaseEvent;
import com.voxstream.core.event.EventType;
import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformStatus;

/**
 * Orchestrates lifecycle of platform connections providing auto-reconnect with
 * exponential backoff and metrics plus status event publication. Phase 3.1
 * evolving implementation.
 */
@Component
public class PlatformConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(PlatformConnectionManager.class);

    private final PlatformConnectionRegistry registry;
    private final ConfigurationService config;
    private final EventBus eventBus;

    private final Map<String, ConnState> states = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "platform-manager");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean started = new AtomicBoolean(false);

    public PlatformConnectionManager(PlatformConnectionRegistry registry, ConfigurationService config,
            EventBus eventBus) {
        this.registry = registry;
        this.config = config;
        this.eventBus = eventBus;
    }

    public void start() {
        if (!started.compareAndSet(false, true))
            return;
        if (!config.get(CoreConfigKeys.PLATFORM_ENABLED)) {
            log.info("Platform connections globally disabled");
            return;
        }
        log.info("PlatformConnectionManager starting (platforms={})", registry.platformIds());
        registry.platformIds().forEach(this::initAndConnectAsync);
    }

    public void shutdown() {
        if (!started.compareAndSet(true, false))
            return;
        scheduler.shutdownNow();
        states.values().forEach(s -> {
            PlatformConnection c = s.connection;
            try {
                if (c.isConnected()) {
                    c.disconnect().whenComplete((v, ex) -> s.metrics.disconnects++);
                } else {
                    c.disconnect();
                }
            } catch (Exception ignored) {
            }
        });
        log.info("PlatformConnectionManager shutdown complete");
    }

    private void initAndConnectAsync(String platformId) {
        PlatformConnection conn = registry.get(platformId).orElseThrow();
        ConnState state = states.computeIfAbsent(platformId, id -> new ConnState(conn));
        // register listener to propagate transitions (for external changes)
        conn.addStatusListener(ps -> state.lastStatus = ps);
        attemptConnect(state, 0, config.get(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS));
    }

    private void attemptConnect(ConnState state, int attempt, int currentDelayMs) {
        String id = state.connection.platformId();
        updateStatus(state, PlatformStatus.connecting());
        log.info("[{}] Attempting connect (attempt={})", id, attempt);
        state.connection.connect().whenComplete((ok, ex) -> {
            if (Boolean.TRUE.equals(ok) && ex == null) {
                state.metrics.connects++;
                long now = Instant.now().toEpochMilli();
                state.lastSuccessfulConnectEpochMs = now;
                updateStatus(state, PlatformStatus.connected(now));
                state.resetBackoff();
            } else {
                state.metrics.failedAttempts++;
                String msg = ex != null ? ex.getMessage() : "failed";
                updateStatus(state, PlatformStatus.failed(msg));
                scheduleReconnect(state, attempt + 1, currentDelayMs);
            }
        });
    }

    private void scheduleReconnect(ConnState state, int nextAttempt, int previousDelayMs) {
        if (!started.get())
            return;
        int maxAttempts = config.get(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS);
        if (maxAttempts != -1 && nextAttempt > maxAttempts) {
            log.warn("[{}] Max reconnect attempts reached -> giving up", state.connection.platformId());
            return;
        }
        int maxDelay = config.get(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS);
        int delay = Math.min(previousDelayMs * 2, maxDelay);
        updateStatus(state, PlatformStatus.reconnectScheduled(delay, nextAttempt));
        state.metrics.currentBackoffMs = delay;
        scheduler.schedule(() -> attemptConnect(state, nextAttempt, delay), delay, TimeUnit.MILLISECONDS);
    }

    private void updateStatus(ConnState state, PlatformStatus newStatus) {
        state.lastStatus = newStatus;
        // publish system status change event
        publishStatusEvent(state.connection.platformId(), newStatus);
    }

    private void publishStatusEvent(String platformId, PlatformStatus status) {
        try {
            ConnState st = states.get(platformId);
            long lastOk = st != null ? st.lastSuccessfulConnectEpochMs : 0L;
            var payload = Map.<String, Object>of(
                    "platform", platformId,
                    "state", status.state().name(),
                    "detail", status.detail(),
                    "connectedSince", status.connectedSinceEpochMs(),
                    "lastSuccessful", lastOk);
            eventBus.publish(new BaseEvent(EventType.SYSTEM, platformId, payload, null));
        } catch (Exception e) {
            log.warn("Failed to publish status event for {}: {}", platformId, e.getMessage());
        }
    }

    public PlatformStatus status(String platformId) {
        return Optional.ofNullable(states.get(platformId)).map(s -> s.lastStatus).orElse(PlatformStatus.disconnected());
    }

    public Map<String, PlatformStatus> snapshot() {
        Map<String, PlatformStatus> snap = new ConcurrentHashMap<>();
        registry.platformIds().forEach(id -> snap.put(id, status(id)));
        return snap;
    }

    public Metrics metrics(String platformId) {
        return Optional.ofNullable(states.get(platformId)).map(s -> s.metrics).orElseGet(Metrics::new);
    }

    // --- Internal State ---
    private static class ConnState {
        final PlatformConnection connection;
        volatile PlatformStatus lastStatus = PlatformStatus.disconnected();
        long lastSuccessfulConnectEpochMs = 0L; // now referenced in publishStatusEvent
        final Metrics metrics = new Metrics();

        ConnState(PlatformConnection c) {
            this.connection = c;
        }

        void resetBackoff() {
            metrics.currentBackoffMs = 0;
        }
    }

    public static class Metrics {
        public long connects;
        public long disconnects;
        public long failedAttempts;
        public int currentBackoffMs;
    }
}
