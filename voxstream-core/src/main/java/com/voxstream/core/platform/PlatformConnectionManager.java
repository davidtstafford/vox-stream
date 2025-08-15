package com.voxstream.core.platform;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
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
    /**
     * Internal test hook: if this system property is set (milliseconds), it
     * overrides
     * the configured seconds interval for status summary scheduling. Not exposed as
     * a
     * ConfigKey, bypasses validators. Intended ONLY for tests.
     */
    static final String TEST_LOG_SUMMARY_INTERVAL_MS_PROP = "vox.platform.status.logSummary.testIntervalMs";

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

    /**
     * Test-only hooks to observe periodic summary without relying on logging
     * backend.
     */
    private final java.util.List<java.util.function.Consumer<java.util.List<SummaryRow>>> testSummaryHooks = new java.util.concurrent.CopyOnWriteArrayList<>();

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
        startLogSummaryIfEnabled();
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
        int initialDelay = config.get(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS);
        state.lastBaseDelayMs = initialDelay; // seed base delay tracker
        attemptConnect(state, 0, initialDelay);
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
                scheduleBackoffResetIfStable(state); // defer reset until stable period elapsed
            } else {
                state.metrics.failedAttempts++;
                // Inspect connection status for fatal flag (scripted outcomes) first
                PlatformStatus connectionReported = state.connection.status();
                boolean fatal = connectionReported.state() == PlatformStatus.State.FAILED && connectionReported.fatal();
                String msg = ex != null && ex.getMessage() != null ? ex.getMessage()
                        : connectionReported.detail() != null ? connectionReported.detail() : "failed";
                PlatformStatus failedStatus = fatal ? PlatformStatus.failed(msg, true) : PlatformStatus.failed(msg);
                updateStatus(state, failedStatus);
                if (failedStatus.fatal()) {
                    log.warn("[{}] Fatal failure -> not scheduling reconnect", id);
                } else {
                    scheduleReconnect(state, attempt + 1);
                }
            }
        });
    }

    private void scheduleReconnect(ConnState state, int nextAttempt) {
        if (!started.get())
            return;
        int maxAttempts = config.get(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS);
        if (maxAttempts != -1 && nextAttempt > maxAttempts) {
            log.warn("[{}] Max reconnect attempts reached -> giving up", state.connection.platformId());
            return;
        }
        int maxDelay = config.get(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS);
        // compute next base from stored base (not jittered) value
        int baseDelay = Math.min(state.lastBaseDelayMs * 2, maxDelay);
        state.lastBaseDelayMs = baseDelay; // persist new base
        int adjustedDelay = baseDelay;
        // Apply jitter AFTER determining base
        double jitterPct = config.get(CoreConfigKeys.PLATFORM_RECONNECT_JITTER_PERCENT);
        if (jitterPct > 0) {
            long jitterRange = Math.round(baseDelay * jitterPct);
            if (jitterRange > 0) {
                long offset = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
                adjustedDelay = (int) Math.max(50, baseDelay + offset); // never below minimal 50ms safety
            }
        }
        updateStatus(state, PlatformStatus.reconnectScheduled(adjustedDelay, nextAttempt));
        state.metrics.currentBackoffMs = adjustedDelay;
        final int scheduleDelay = adjustedDelay;
        scheduler.schedule(() -> attemptConnect(state, nextAttempt, scheduleDelay), scheduleDelay,
                TimeUnit.MILLISECONDS);
    }

    private void scheduleBackoffResetIfStable(ConnState state) {
        int thresholdMs = config.get(CoreConfigKeys.PLATFORM_RECONNECT_RESET_AFTER_STABLE_MS);
        if (thresholdMs <= 0)
            return;
        int currentBackoffAtConnect = state.metrics.currentBackoffMs; // capture value
        if (currentBackoffAtConnect == 0)
            return; // nothing to reset
        scheduler.schedule(() -> {
            // Only reset if still connected & backoff unchanged & service running
            if (!started.get())
                return;
            if (state.connection.isConnected() && state.metrics.currentBackoffMs == currentBackoffAtConnect) {
                log.info("[{}] Stable connection for {}ms -> resetting backoff (was {}ms)",
                        state.connection.platformId(), thresholdMs, currentBackoffAtConnect);
                state.resetBackoff();
            }
        }, thresholdMs, TimeUnit.MILLISECONDS);
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
                    "lastSuccessful", lastOk,
                    "fatal", status.fatal());
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

    public void addTestSummaryHook(java.util.function.Consumer<java.util.List<SummaryRow>> hook) {
        if (hook != null) {
            testSummaryHooks.add(hook);
        }
    }

    public static record SummaryRow(String platformId, PlatformStatus.State state, boolean fatal, long connects,
            long failedAttempts, int backoffMs) {
    }

    private void startLogSummaryIfEnabled() {
        if (!config.get(CoreConfigKeys.PLATFORM_STATUS_LOG_SUMMARY_ENABLED))
            return;
        int intervalSec = config.get(CoreConfigKeys.PLATFORM_STATUS_LOG_SUMMARY_INTERVAL_SEC);
        long overrideMs = -1;
        try {
            String prop = System.getProperty(TEST_LOG_SUMMARY_INTERVAL_MS_PROP);
            if (prop != null) {
                overrideMs = Long.parseLong(prop.trim());
            }
        } catch (NumberFormatException ignored) {
        }
        Runnable task = () -> {
            if (!started.get())
                return;
            try {
                java.util.List<SummaryRow> rows = new java.util.ArrayList<>();
                registry.platformIds().forEach(id -> {
                    PlatformStatus st = status(id);
                    Metrics m = metrics(id);
                    log.info("[status-summary] platform={} state={} fatal={} connects={} fails={} backoffMs={}", id,
                            st.state(), st.fatal(), m.connects, m.failedAttempts, m.currentBackoffMs);
                    rows.add(new SummaryRow(id, st.state(), st.fatal(), m.connects, m.failedAttempts,
                            m.currentBackoffMs));
                });
                if (!testSummaryHooks.isEmpty()) {
                    for (var h : testSummaryHooks) {
                        try {
                            h.accept(rows);
                        } catch (Exception e) {
                            log.debug("Test summary hook error: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Status summary failed: {}", e.getMessage());
            }
        };
        if (overrideMs > 0) {
            long intervalMs = overrideMs;
            scheduler.scheduleAtFixedRate(task, intervalMs, intervalMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return;
        }
        scheduler.scheduleAtFixedRate(task, intervalSec, intervalSec, java.util.concurrent.TimeUnit.SECONDS);
    }

    // --- Internal State ---
    private static class ConnState {
        final PlatformConnection connection;
        volatile PlatformStatus lastStatus = PlatformStatus.disconnected();
        long lastSuccessfulConnectEpochMs = 0L; // now referenced in publishStatusEvent
        final Metrics metrics = new Metrics();
        volatile int lastBaseDelayMs = 0; // tracks exponential progression independent of jitter

        ConnState(PlatformConnection c) {
            this.connection = c;
        }

        void resetBackoff() {
            metrics.currentBackoffMs = 0;
            // leave lastBaseDelayMs untouched; next failure will double from existing base
        }
    }

    public static class Metrics {
        public long connects;
        public long disconnects;
        public long failedAttempts;
        public volatile int currentBackoffMs; // volatile for cross-thread visibility (tests read async)
    }
}
