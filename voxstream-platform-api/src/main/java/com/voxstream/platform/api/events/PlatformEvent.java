package com.voxstream.platform.api.events;

import java.time.Instant;

/** Base abstraction for raw platform events before mapping to core models. */
public interface PlatformEvent {
    Instant timestamp();

    String platform();

    String type();
}
