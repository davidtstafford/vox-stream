package com.voxstream.core.config.validation;

import java.util.Map;

import com.voxstream.core.config.keys.ConfigKey;
import com.voxstream.core.config.keys.CoreConfigKeys;

/**
 * Holds cross-field (composite) validation logic.
 * Called by ConfigValidators.validateComposite() with a snapshot of proposed
 * values.
 */
public final class CompositeConfigValidators {
    private CompositeConfigValidators() {
    }

    public static void validate(Map<ConfigKey<?>, Object> values) {
        // Rule: TTS purge interval must be >= 1 (already enforced individually) and
        // >= event purge interval (ensures TTS queue not cleaned more frequently than
        // events)
        Integer eventPurge = get(values, CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, Integer.class);
        Integer ttsPurge = get(values, CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, Integer.class);
        if (eventPurge != null && ttsPurge != null && ttsPurge < eventPurge) {
            throw new IllegalArgumentException("TTS purge interval must be >= Event purge interval");
        }
        // Rule: web port must differ from reserved sentinel (65535)
        Integer webPort = get(values, CoreConfigKeys.WEB_OUTPUT_PORT, Integer.class);
        if (webPort != null && webPort == 65535) {
            throw new IllegalArgumentException("Port 65535 reserved for internal use");
        }
        // Rule: platform reconnect max delay must be >= initial delay
        Integer initial = get(values, CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, Integer.class);
        Integer max = get(values, CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, Integer.class);
        if (initial != null && max != null && max < initial) {
            throw new IllegalArgumentException(
                    "platform.reconnect.maxDelayMs must be >= platform.reconnect.initialDelayMs");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Map<ConfigKey<?>, Object> values, ConfigKey<T> key, Class<T> type) {
        Object v = values.get(key);
        if (v == null)
            return null;
        return (T) v;
    }
}
