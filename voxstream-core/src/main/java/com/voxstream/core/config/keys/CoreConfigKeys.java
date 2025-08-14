package com.voxstream.core.config.keys;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central definition of configuration keys used in core modules.
 */
public final class CoreConfigKeys {
    private CoreConfigKeys() {
    }

    // Internal management key (not user editable)
    public static final ConfigKey<Integer> CONFIG_SCHEMA_VERSION = new ConfigKey<>(
            "config.schema.version", Integer.class, 1, false,
            "Configuration schema version (managed automatically by migrations)");

    // Internal hash of last exported full settings snapshot (for change detection)
    public static final ConfigKey<String> LAST_EXPORT_HASH = new ConfigKey<>(
            "config.lastExportHash", String.class, "", false,
            "Hash of last exported settings snapshot (managed internally)");

    // Default profile name to auto-apply on startup (blank = none)
    public static final ConfigKey<String> DEFAULT_PROFILE_NAME = new ConfigKey<>(
            "config.profile.defaultName", String.class, "", false,
            "Name of profile auto-applied at startup");

    // Event bus related
    public static final ConfigKey<Integer> EVENT_BUS_MAX_EVENTS = new ConfigKey<>(
            "event.bus.maxEvents", Integer.class, 10000, false, "Maximum buffered in-memory events");

    public static final ConfigKey<Integer> EVENT_BUS_PURGE_INTERVAL_MIN = new ConfigKey<>(
            "event.bus.purgeIntervalMinutes", Integer.class, 10, false, "Event purge interval in minutes");

    // TTS related placeholder keys
    public static final ConfigKey<Boolean> TTS_ENABLED = new ConfigKey<>(
            "tts.enabled", Boolean.class, Boolean.FALSE, false, "Enable Text-to-Speech");

    public static final ConfigKey<String> TTS_VOICE = new ConfigKey<>(
            "tts.voice", String.class, "Joanna", false, "Default TTS voice");

    // TTS bus purge (future TTS event queue)
    public static final ConfigKey<Integer> TTS_BUS_PURGE_INTERVAL_MIN = new ConfigKey<>(
            "tts.bus.purgeIntervalMinutes", Integer.class, 30, false, "TTS bus purge interval in minutes");

    // Web output settings
    public static final ConfigKey<Integer> WEB_OUTPUT_PORT = new ConfigKey<>(
            "web.port", Integer.class, 8080, false, "Embedded web output port");

    public static final ConfigKey<Boolean> WEB_OUTPUT_ENABLE_CORS = new ConfigKey<>(
            "web.cors.enabled", Boolean.class, Boolean.TRUE, false, "Enable CORS for web output");

    // --- Platform connection framework (Phase 3.1) ---
    // Global enable toggle (future: may add per-platform granular keys)
    public static final ConfigKey<Boolean> PLATFORM_ENABLED = new ConfigKey<>(
            "platform.enabled", Boolean.class, Boolean.FALSE, false,
            "Enable platform connections globally");

    public static final ConfigKey<Integer> PLATFORM_RECONNECT_INITIAL_DELAY_MS = new ConfigKey<>(
            "platform.reconnect.initialDelayMs", Integer.class, 1000, false,
            "Initial reconnect backoff delay in milliseconds");

    public static final ConfigKey<Integer> PLATFORM_RECONNECT_MAX_DELAY_MS = new ConfigKey<>(
            "platform.reconnect.maxDelayMs", Integer.class, 30000, false,
            "Maximum reconnect backoff delay in milliseconds");

    public static final ConfigKey<Integer> PLATFORM_RECONNECT_MAX_ATTEMPTS = new ConfigKey<>(
            "platform.reconnect.maxAttempts", Integer.class, -1, false,
            "Maximum reconnect attempts (-1 = infinite)");

    public static final ConfigKey<Integer> PLATFORM_HEARTBEAT_INTERVAL_SEC = new ConfigKey<>(
            "platform.heartbeat.intervalSec", Integer.class, 60, false,
            "Heartbeat / synthetic event emission interval in seconds");

    // Registry of all keys for iteration / import-export (order preserved)
    private static final Map<String, ConfigKey<?>> REGISTRY;
    public static final List<ConfigKey<?>> ALL;

    static {
        Map<String, ConfigKey<?>> m = new LinkedHashMap<>();
        register(m, CONFIG_SCHEMA_VERSION);
        register(m, LAST_EXPORT_HASH);
        register(m, DEFAULT_PROFILE_NAME);
        register(m, EVENT_BUS_MAX_EVENTS);
        register(m, EVENT_BUS_PURGE_INTERVAL_MIN);
        register(m, TTS_ENABLED);
        register(m, TTS_VOICE);
        register(m, TTS_BUS_PURGE_INTERVAL_MIN);
        register(m, WEB_OUTPUT_PORT);
        register(m, WEB_OUTPUT_ENABLE_CORS);
        // Platform keys appended (Phase 3)
        register(m, PLATFORM_ENABLED);
        register(m, PLATFORM_RECONNECT_INITIAL_DELAY_MS);
        register(m, PLATFORM_RECONNECT_MAX_DELAY_MS);
        register(m, PLATFORM_RECONNECT_MAX_ATTEMPTS);
        register(m, PLATFORM_HEARTBEAT_INTERVAL_SEC);
        REGISTRY = Collections.unmodifiableMap(m);
        ALL = List.copyOf(REGISTRY.values());
    }

    private static void register(Map<String, ConfigKey<?>> m, ConfigKey<?> key) {
        m.put(key.getName(), key);
    }

    public static ConfigKey<?> byName(String name) {
        return REGISTRY.get(name);
    }
}
