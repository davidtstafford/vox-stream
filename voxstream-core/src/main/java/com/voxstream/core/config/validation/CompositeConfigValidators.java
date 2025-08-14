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
        // Example rule: if TTS enabled (future key) ensure purge interval not less than
        // event bus.
        // (Placeholder: once TTS_ENABLED wired to UI we can enforce meaningful
        // relations.)
        Integer eventPurge = get(values, CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, Integer.class);
        Integer ttsPurge = get(values, CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, Integer.class);
        if (eventPurge != null && ttsPurge != null && ttsPurge < 1) {
            throw new IllegalArgumentException("TTS purge interval must be >= 1 minute");
        }
        // Rule: web port must differ from 0 (already primitive rule) and not equal a
        // reserved sentinel (e.g. 65535)
        Integer webPort = get(values, CoreConfigKeys.WEB_OUTPUT_PORT, Integer.class);
        if (webPort != null && webPort == 65535) {
            throw new IllegalArgumentException("Port 65535 reserved for internal use");
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
