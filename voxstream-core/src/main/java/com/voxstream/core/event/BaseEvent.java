package com.voxstream.core.event;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Simple base event implementation.
 */
public class BaseEvent implements Event {
    private final String id;
    private final EventType type;
    private final Instant timestamp;
    private final String sourcePlatform;
    private final Map<String, Object> payload;
    private final EventMetadata metadata;

    public BaseEvent(EventType type, String sourcePlatform, Map<String, Object> payload, EventMetadata metadata) {
        this(Event.newId(), type, sourcePlatform, payload, metadata);
    }

    public BaseEvent(String id, EventType type, String sourcePlatform, Map<String, Object> payload,
            EventMetadata metadata) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.timestamp = Instant.now();
        this.sourcePlatform = Objects.requireNonNull(sourcePlatform);
        this.payload = payload != null ? Collections.unmodifiableMap(payload) : Collections.emptyMap();
        this.metadata = metadata;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public EventType getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getSourcePlatform() {
        return sourcePlatform;
    }

    @Override
    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public EventMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "BaseEvent{" + "id='" + id + '\'' + ", type=" + type + ", timestamp=" + timestamp + ", sourcePlatform='"
                + sourcePlatform + '\'' + ", payloadSize=" + payload.size() + '}';
    }
}
