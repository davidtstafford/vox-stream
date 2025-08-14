package com.voxstream.platform.api;

/**
 * Immutable status snapshot for a platform connection.
 */
public record PlatformStatus(
        State state,
        String detail,
        long connectedSinceEpochMs) {

    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED,
        RECONNECT_SCHEDULED
    }

    public static PlatformStatus disconnected() {
        return new PlatformStatus(State.DISCONNECTED, "disconnected", 0L);
    }

    public static PlatformStatus connecting() {
        return new PlatformStatus(State.CONNECTING, "connecting", 0L);
    }

    public static PlatformStatus connected(long sinceEpochMs) {
        return new PlatformStatus(State.CONNECTED, "connected", sinceEpochMs);
    }

    public static PlatformStatus failed(String message) {
        return new PlatformStatus(State.FAILED, message, 0L);
    }

    public static PlatformStatus reconnectScheduled(long delayMs, int attempt) {
        return new PlatformStatus(State.RECONNECT_SCHEDULED,
                "reconnect in " + delayMs + "ms (attempt=" + attempt + ")", 0L);
    }
}
