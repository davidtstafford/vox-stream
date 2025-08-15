package com.voxstream.platform.api;

import java.util.Collections;
import java.util.Set;

/**
 * Descriptive metadata for a platform connector used by UI and dynamic
 * configuration. Immutable record-style class (no record to keep Java 17
 * compatibility nuances minimal).
 */
public final class PlatformMetadata {
    private final String id;
    private final String displayName;
    private final String version;
    private final Set<Capability> capabilities;

    public PlatformMetadata(String id, String displayName, String version, Set<Capability> capabilities) {
        this.id = id;
        this.displayName = displayName;
        this.version = version;
        this.capabilities = capabilities == null ? Set.of() : Collections.unmodifiableSet(capabilities);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String version() {
        return version;
    }

    public Set<Capability> capabilities() {
        return capabilities;
    }
}
