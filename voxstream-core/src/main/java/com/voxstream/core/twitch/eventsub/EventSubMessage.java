package com.voxstream.core.twitch.eventsub;

import java.time.Instant;
import java.util.Map;

/** Represents a raw EventSub WebSocket message (after JSON parsing). */
public record EventSubMessage(String metadataType, String subscriptionType, String subscriptionVersion,
        Instant receivedAt, Map<String, Object> payload) {
}
