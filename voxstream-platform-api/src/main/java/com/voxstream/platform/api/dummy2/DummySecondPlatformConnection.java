package com.voxstream.platform.api.dummy2;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformStatus;
import com.voxstream.platform.api.events.PlatformEvent;

/**
 * Always-connected no-op platform used to verify multi-platform registry and UI
 * dynamics.
 */
public class DummySecondPlatformConnection implements PlatformConnection {
    public static final String PLATFORM_ID = "dummy2";

    private volatile PlatformStatus status = PlatformStatus.disconnected();
    private final List<Consumer<PlatformStatus>> statusListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlatformEvent>> eventListeners = new CopyOnWriteArrayList<>();

    @Override
    public CompletableFuture<Boolean> connect() {
        status = PlatformStatus.connected(Instant.now().toEpochMilli());
        statusListeners.forEach(l -> l.accept(status));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        status = PlatformStatus.disconnected();
        statusListeners.forEach(l -> l.accept(status));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public PlatformStatus status() {
        return status;
    }

    @Override
    public void addStatusListener(Consumer<PlatformStatus> listener) {
        statusListeners.add(listener);
    }

    @Override
    public void addPlatformEventListener(Consumer<PlatformEvent> listener) {
        eventListeners.add(listener);
    }

    @Override
    public String platformId() {
        return PLATFORM_ID;
    }
}
