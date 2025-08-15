package com.voxstream.core.twitch.eventsub;

import java.time.Instant;
import java.util.Map;

import com.voxstream.platform.api.events.PlatformEvent;

/**
 * PlatformEvent implementation for Twitch EventSub messages after conversion.
 */
public record TwitchEventPlatformEvent(Instant timestamp, String platform, String type, Map<String, Object> payload)
        implements PlatformEvent {
}
