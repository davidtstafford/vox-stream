package com.voxstream.core.model;

import java.time.Instant;
import java.util.Objects;

public class Viewer {
    private final String id; // internal unique id
    private final String platform; // e.g. TWITCH
    private final String handle; // platform username/handle (case-insensitive store lower?)
    private final String displayName; // original casing
    private final Instant createdAt;
    private final Instant lastSeenAt;

    public Viewer(String id, String platform, String handle, String displayName, Instant createdAt,
            Instant lastSeenAt) {
        this.id = Objects.requireNonNull(id);
        this.platform = Objects.requireNonNull(platform);
        this.handle = Objects.requireNonNull(handle);
        this.displayName = displayName != null ? displayName : handle;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt);
    }

    public String getId() {
        return id;
    }

    public String getPlatform() {
        return platform;
    }

    public String getHandle() {
        return handle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
