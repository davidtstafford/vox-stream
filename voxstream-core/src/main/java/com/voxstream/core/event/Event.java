package com.voxstream.core.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Core immutable Event contract.
 */
public interface Event {
    String getId();

    EventType getType();

    Instant getTimestamp();

    String getSourcePlatform();

    Map<String, Object> getPayload();

    EventMetadata getMetadata();

    default boolean isExpired() {
        return getMetadata() != null && getMetadata().getExpiresAt().map(e -> Instant.now().isAfter(e)).orElse(false);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }
}
