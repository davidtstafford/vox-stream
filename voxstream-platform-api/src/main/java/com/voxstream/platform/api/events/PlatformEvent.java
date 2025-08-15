package com.voxstream.platform.api.events;

import java.time.Instant;
import java.util.Map;

/** Base abstraction for raw platform events before mapping to core models. */
public interface PlatformEvent {
    Instant timestamp();

    String platform();

    String type();

    /** Raw structured payload from the platform (immutable or defensive copy). */
    default Map<String, Object> payload() {
        return java.util.Collections.emptyMap();
    }
}
