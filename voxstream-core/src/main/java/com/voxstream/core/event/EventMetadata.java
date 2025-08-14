package com.voxstream.core.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional metadata attached to events for filtering/routing purposes.
 */
public class EventMetadata {
    private final String viewerId;
    private final String channelId;
    private final int importance; // 0 = normal, higher = more important
    private final Instant expiresAt; // Optional expiration
    private final String correlationId;

    private EventMetadata(Builder builder) {
        this.viewerId = builder.viewerId;
        this.channelId = builder.channelId;
        this.importance = builder.importance;
        this.expiresAt = builder.expiresAt;
        this.correlationId = builder.correlationId != null ? builder.correlationId : UUID.randomUUID().toString();
    }

    public Optional<String> getViewerId() {
        return Optional.ofNullable(viewerId);
    }

    public Optional<String> getChannelId() {
        return Optional.ofNullable(channelId);
    }

    public int getImportance() {
        return importance;
    }

    public Optional<Instant> getExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String viewerId;
        private String channelId;
        private int importance = 0;
        private Instant expiresAt;
        private String correlationId;

        public Builder viewerId(String viewerId) {
            this.viewerId = viewerId;
            return this;
        }

        public Builder channelId(String channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder importance(int importance) {
            this.importance = importance;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public EventMetadata build() {
            return new EventMetadata(this);
        }
    }

    @Override
    public String toString() {
        return "EventMetadata{" +
                "viewerId='" + viewerId + '\'' +
                ", channelId='" + channelId + '\'' +
                ", importance=" + importance +
                ", expiresAt=" + expiresAt +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof EventMetadata))
            return false;
        EventMetadata that = (EventMetadata) o;
        return importance == that.importance && Objects.equals(viewerId, that.viewerId)
                && Objects.equals(channelId, that.channelId) && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewerId, channelId, importance, expiresAt, correlationId);
    }
}
