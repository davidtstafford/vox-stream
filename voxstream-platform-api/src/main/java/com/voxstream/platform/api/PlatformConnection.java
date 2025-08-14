package com.voxstream.platform.api;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a connection to a streaming platform (e.g., Twitch).
 * Lifecycle is asynchronous to avoid blocking the JavaFX or event threads.
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
     * @return current status snapshot
     */
    PlatformStatus status();

    /**
     * @return platform identifier (e.g. "twitch")
     */
    String platformId();
}
