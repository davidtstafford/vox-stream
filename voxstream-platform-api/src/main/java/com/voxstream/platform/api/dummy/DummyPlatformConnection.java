package com.voxstream.platform.api.dummy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformStatus;
import com.voxstream.platform.api.PlatformStatus.State;

/**
 * In-memory dummy implementation used for early Phase 3 wiring & testing.
 * Simulates a connection delay and transitions through states.
 */
public class DummyPlatformConnection implements PlatformConnection {

    private static final Logger log = LoggerFactory.getLogger(DummyPlatformConnection.class);
    public static final String PLATFORM_ID = "dummy";

    private final AtomicReference<PlatformStatus> status = new AtomicReference<>(PlatformStatus.disconnected());
    private final List<Consumer<PlatformStatus>> listeners = new CopyOnWriteArrayList<>();
    private volatile String sessionId;

    // Failure injection: a sequence of booleans indicating success (true) or failure (false) for successive connect attempts
    private volatile List<Boolean> scriptedOutcomes = List.of();
    private volatile int outcomeIndex = 0;
    private volatile long simulatedLatencyMs = 250;
    @SuppressWarnings("unused")
    private volatile boolean emitSyntheticEvents = false; // placeholder for future event emission toggle

    /** Configure a sequence of outcomes for upcoming connect() attempts. */
    public void setScriptedOutcomes(List<Boolean> outcomes) {
        this.scriptedOutcomes = outcomes != null ? List.copyOf(outcomes) : List.of();
        this.outcomeIndex = 0;
    }

    /** Adjust artificial latency for connect attempts. */
    public void setSimulatedLatencyMs(long ms) { this.simulatedLatencyMs = ms; }

    /** Toggle synthetic platform event emission (future hook). */
    public void setEmitSyntheticEvents(boolean enable) { this.emitSyntheticEvents = enable; }

    @Override
    public CompletableFuture<Boolean> connect() {
        if (status.get().state() == State.CONNECTED) {
            return CompletableFuture.completedFuture(true);
        }
        updateStatus(PlatformStatus.connecting());
        sessionId = UUID.randomUUID().toString();
        log.info("[DummyPlatform] Connecting session {}...", sessionId);
        boolean scriptedFailure = false;
        if (outcomeIndex < scriptedOutcomes.size()) {
            boolean outcome = scriptedOutcomes.get(outcomeIndex++);
            scriptedFailure = !outcome;
        }
        final boolean fail = scriptedFailure;
        long latency = simulatedLatencyMs;
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(latency);
                if (fail) {
                    updateStatus(PlatformStatus.failed("scripted-failure"));
                    return false;
                }
                PlatformStatus connected = PlatformStatus.connected(Instant.now().toEpochMilli());
                updateStatus(connected);
                log.info("[DummyPlatform] Connected session {}", sessionId);
                // future: if emitSyntheticEvents then start emitting events
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateStatus(PlatformStatus.failed("interrupted"));
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        if (status.get().state() == State.DISCONNECTED) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            log.info("[DummyPlatform] Disconnecting session {}", sessionId);
            updateStatus(PlatformStatus.disconnected());
            sessionId = null;
        });
    }

    @Override
    public PlatformStatus status() {
        return status.get();
    }

    @Override
    public void addStatusListener(java.util.function.Consumer<PlatformStatus> listener) {
        listeners.add(listener);
    }

    private void updateStatus(PlatformStatus newStatus) {
        status.set(newStatus);
        listeners.forEach(l -> {
            try {
                l.accept(newStatus);
            } catch (Exception e) {
                log.warn("Listener error: {}", e.getMessage());
            }
        });
    }

    @Override
    public String platformId() {
        return PLATFORM_ID;
    }
}
