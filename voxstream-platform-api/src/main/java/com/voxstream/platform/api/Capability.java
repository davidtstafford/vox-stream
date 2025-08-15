package com.voxstream.platform.api;

/**
 * Enumerates optional capabilities a platform implementation can expose.
 */
public enum Capability {
    EVENTS,
    CHAT,
    TTS_INPUT,
    RESPONSES,
    WEBHOOK,
    IRC_CHAT;
}
