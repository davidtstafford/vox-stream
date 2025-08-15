package com.voxstream.platform.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.voxstream.platform.api.events.PlatformEvent;

/**
 * Represents a connection to a streaming platform (e.g., Twitch).
 * Lifecycle is asynchronous to avoid blocking UI or event threads.
 */
public interface PlatformConnection {

    /**
     * Initiates the connection asynchronously.
     *
     * @return future completing true if connection established, false otherwise
     */
    CompletableFuture<Boolean> connect();

    /**
     * Disconnects gracefully. Should be idempotent.
     *
     * @return future signaling completion
     */
    CompletableFuture<Void> disconnect();

    /**
     * Request a reconnect cycle (implementation may decide to schedule based on
     * backoff logic).
     */
    default void requestReconnect() {
        // optional override by implementations
    }

    /**
     * @return true if currently in CONNECTED state.
     */
    default boolean isConnected() {
        return status().state() == PlatformStatus.State.CONNECTED;
    }

    /**
     * @return current status snapshot
     */
    PlatformStatus status();

    /**
     * Register a listener for status changes.
     */
    default void addStatusListener(Consumer<PlatformStatus> listener) {
        // optional; implementations supporting listeners override
    }

    /**
     * Register a listener for raw platform events (pre-mapping). Optional.
     */
    default void addPlatformEventListener(Consumer<PlatformEvent> listener) {
        // optional; implementations supporting events override
    }

    /**
     * Perform a lightweight health check (optional).
     */
    default CompletableFuture<Boolean> healthCheck() {
        return CompletableFuture.completedFuture(isConnected());
    }

    /**
     * @return platform identifier (e.g. "twitch")
     */
    String platformId();
}
