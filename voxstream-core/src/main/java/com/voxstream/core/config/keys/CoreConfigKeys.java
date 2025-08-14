package com.voxstream.core.config.keys;

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
}
