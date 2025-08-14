package com.voxstream.platform.api;

/**
 * Simple immutable status snapshot for a platform connection.
 */
public record PlatformStatus(
        State state,
        String detail,
        long connectedSinceEpochMs) {

    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    public static PlatformStatus disconnected() {
        return new PlatformStatus(State.DISCONNECTED, "disconnected", 0L);
    }

    public static PlatformStatus connecting() {
        return new PlatformStatus(State.CONNECTING, "connecting", 0L);
    }

    public static PlatformStatus error(String message) {
        return new PlatformStatus(State.ERROR, message, 0L);
    }
}
