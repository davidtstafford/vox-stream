package com.voxstream.platform.api.dummy;

import java.time.Clock;
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
import com.voxstream.platform.api.events.PlatformEvent;

/**
 * In-memory dummy implementation used for early Phase 3 wiring & testing.
 * Simulates a connection delay and transitions through states.
 */
public class DummyPlatformConnection implements PlatformConnection {

    private static final Logger log = LoggerFactory.getLogger(DummyPlatformConnection.class);
    public static final String PLATFORM_ID = "dummy";

    private final AtomicReference<PlatformStatus> status = new AtomicReference<>(PlatformStatus.disconnected());
    private final List<Consumer<PlatformStatus>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlatformEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private volatile String sessionId;

    // Failure injection: A sequence describing outcomes for connect attempts.
    // Supported tokens:
    // true -> success
    // false -> transient failure (retry)
    // F -> fatal failure (retry stops)
    private volatile List<Object> scriptedOutcomes = List.of();
    private volatile int outcomeIndex = 0;
    private volatile long simulatedLatencyMs = 250;
    private volatile boolean emitSyntheticEvents = false; // now used
    private volatile Clock clock = Clock.systemUTC();
    private volatile long syntheticEventCounter = 0;

    /** Configure a sequence of outcomes for upcoming connect() attempts. */
    public void setScriptedOutcomes(List<?> outcomes) {
        this.scriptedOutcomes = outcomes != null ? List.copyOf(outcomes) : List.of();
        this.outcomeIndex = 0;
    }

    /** Adjust artificial latency for connect attempts. */
    public void setSimulatedLatencyMs(long ms) {
        this.simulatedLatencyMs = ms;
    }

    /** Toggle synthetic platform event emission (future hook). */
    public void setEmitSyntheticEvents(boolean enable) {
        this.emitSyntheticEvents = enable;
    }

    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void tickSyntheticEvent() {
        if (!emitSyntheticEvents || status.get().state() != State.CONNECTED)
            return;
        long n = ++syntheticEventCounter;
        PlatformEvent evt = new SimplePlatformEvent(clock.instant(), PLATFORM_ID, "synthetic", n);
        log.debug("[DummyPlatform] Synthetic event #{} at {}", n, evt.timestamp());
        firePlatformEvent(evt);
    }

    public long syntheticEventCount() {
        return syntheticEventCounter;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        if (status.get().state() == State.CONNECTED) {
            return CompletableFuture.completedFuture(true);
        }
        updateStatus(PlatformStatus.connecting());
        sessionId = UUID.randomUUID().toString();
        log.info("[DummyPlatform] Connecting session {}...", sessionId);
        Outcome outcome = determineOutcome();
        long latency = simulatedLatencyMs;
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(latency);
                if (!outcome.success) {
                    updateStatus(PlatformStatus.failed(outcome.fatal ? "fatal" : "scripted-failure", outcome.fatal));
                    return false;
                }
                PlatformStatus connected = PlatformStatus.connected(Instant.now().toEpochMilli());
                updateStatus(connected);
                log.info("[DummyPlatform] Connected session {}", sessionId);
                if (emitSyntheticEvents) {
                    tickSyntheticEvent(); // emit one immediately for visibility
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateStatus(PlatformStatus.failed("interrupted", false));
                return false;
            }
        });
    }

    private Outcome determineOutcome() {
        if (outcomeIndex < scriptedOutcomes.size()) {
            Object token = scriptedOutcomes.get(outcomeIndex++);
            if (token instanceof Boolean b) {
                return new Outcome(b, false);
            }
            if (token instanceof String s && s.equalsIgnoreCase("F")) {
                return new Outcome(false, true);
            }
        }
        return new Outcome(true, false); // default success
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

    @Override
    public void addPlatformEventListener(Consumer<PlatformEvent> listener) {
        eventListeners.add(listener);
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

    private void firePlatformEvent(PlatformEvent evt) {
        eventListeners.forEach(l -> {
            try {
                l.accept(evt);
            } catch (Exception e) {
                log.warn("Event listener error: {}", e.getMessage());
            }
        });
    }

    @Override
    public String platformId() {
        return PLATFORM_ID;
    }

    private record Outcome(boolean success, boolean fatal) {
    }

    private record SimplePlatformEvent(Instant timestamp, String platform, String type, long sequence)
            implements PlatformEvent {
    }
}
