package com.voxstream.core.config.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import com.voxstream.core.config.keys.ConfigKey;
import com.voxstream.core.config.keys.CoreConfigKeys;

/**
 * Central registry for configuration value validators.
 * Lightweight (no external libs) and executed inside
 * ConfigurationService.set()/batch update.
 */
public final class ConfigValidators {

    private static final List<ValidatorEntry<?>> ENTRIES = new ArrayList<>();

    static {
        // Event purge interval 1-1440 minutes
        register(CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, v -> v >= 1 && v <= 1440,
                (k, v) -> fail(k, "must be between 1 and 1440 minutes"));
        // TTS purge interval 1-1440
        register(CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, v -> v >= 1 && v <= 1440,
                (k, v) -> fail(k, "must be between 1 and 1440 minutes"));
        // Max events reasonable upper bound
        register(CoreConfigKeys.EVENT_BUS_MAX_EVENTS, v -> v >= 100 && v <= 5_000_000,
                (k, v) -> fail(k, "must be between 100 and 5,000,000"));
        // Web port range
        register(CoreConfigKeys.WEB_OUTPUT_PORT, v -> v >= 1024 && v <= 65535,
                (k, v) -> fail(k, "must be an unprivileged port 1024-65535"));
        // Platform reconnect initial delay 100-60000 ms
        register(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, v -> v >= 100 && v <= 60_000,
                (k, v) -> fail(k, "must be between 100 and 60000 ms"));
        // Platform reconnect max delay 100-300000 ms (composite must be >= initial)
        register(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, v -> v >= 100 && v <= 300_000,
                (k, v) -> fail(k, "must be between 100 and 300000 ms"));
        // Platform max attempts -1 (infinite) or >=1
        register(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, v -> v == -1 || v >= 1,
                (k, v) -> fail(k, "must be -1 (infinite) or >= 1"));
        // Heartbeat interval 5-3600 seconds
        register(CoreConfigKeys.PLATFORM_HEARTBEAT_INTERVAL_SEC, v -> v >= 5 && v <= 3600,
                (k, v) -> fail(k, "must be between 5 and 3600 seconds"));
        // Jitter percent 0.0 - 0.5
        register(CoreConfigKeys.PLATFORM_RECONNECT_JITTER_PERCENT, v -> v >= 0.0 && v <= 0.5,
                (k, v) -> fail(k, "must be between 0.0 and 0.5"));
        // Backoff reset threshold 10s - 1h (ensure reasonable)
        register(CoreConfigKeys.PLATFORM_RECONNECT_RESET_AFTER_STABLE_MS, v -> v >= 10_000 && v <= 3_600_000,
                (k, v) -> fail(k, "must be between 10000 and 3600000 ms"));
        // Periodic status log summary interval 60-3600 seconds
        register(CoreConfigKeys.PLATFORM_STATUS_LOG_SUMMARY_INTERVAL_SEC, v -> v >= 60 && v <= 3600,
                (k, v) -> fail(k, "must be between 60 and 3600 seconds"));
        // Twitch specific
        register(CoreConfigKeys.TWITCH_REDIRECT_PORT, v -> v >= 1024 && v <= 65535,
                (k, v) -> fail(k, "must be an unprivileged port 1024-65535"));
        register(CoreConfigKeys.TWITCH_TOKEN_VALIDATION_INTERVAL_SEC, v -> v >= 60 && v <= 3600,
                (k, v) -> fail(k, "must be between 60 and 3600 seconds"));
        // Twitch EventSub heartbeat interval 30-600 seconds (Twitch typically ~240s)
        register(CoreConfigKeys.TWITCH_EVENTSUB_HEARTBEAT_INTERVAL_SEC, v -> v >= 30 && v <= 600,
                (k, v) -> fail(k, "must be between 30 and 600 seconds"));
    }

    private ConfigValidators() {
    }

    private static <T> void register(ConfigKey<T> key, Predicate<T> predicate, BiConsumer<ConfigKey<T>, T> onFail) {
        ENTRIES.add(new ValidatorEntry<>(key, predicate, onFail));
    }

    public static <T> void validate(ConfigKey<T> key, T value) {
        for (ValidatorEntry<?> ve : ENTRIES) {
            if (ve.key == key) {
                @SuppressWarnings("unchecked")
                ValidatorEntry<T> cast = (ValidatorEntry<T>) ve;
                if (!cast.predicate.test(value)) {
                    cast.onFail.accept(key, value);
                }
                return; // only first matching key
            }
        }
    }

    // Composite validation executed after a batch update (see
    // CompositeConfigValidators)
    public static void validateComposite(Map<ConfigKey<?>, Object> allValues) {
        CompositeConfigValidators.validate(allValues);
    }

    private static void fail(ConfigKey<?> key, String msg) {
        throw new IllegalArgumentException("Invalid value for " + key.getName() + ": " + msg);
    }

    private static final class ValidatorEntry<T> {
        final ConfigKey<T> key;
        final Predicate<T> predicate;
        final BiConsumer<ConfigKey<T>, T> onFail;

        ValidatorEntry(ConfigKey<T> key, Predicate<T> predicate, BiConsumer<ConfigKey<T>, T> onFail) {
            this.key = key;
            this.predicate = predicate;
            this.onFail = onFail;
        }
    }
}
