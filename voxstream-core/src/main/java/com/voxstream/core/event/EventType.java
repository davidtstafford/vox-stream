package com.voxstream.core.event;

/**
 * Basic event types supported by the Event Bus. This list will expand as
 * platform integrations grow.
 */
public enum EventType {
    CHAT_MESSAGE,
    SUBSCRIPTION,
    FOLLOW,
    RAID,
    BITS,
    SYSTEM,
    TTS_REQUEST,
    CHANNEL_POINT,
    HOST,
    UNKNOWN
}
