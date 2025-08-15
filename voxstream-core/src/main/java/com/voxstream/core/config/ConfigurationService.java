package com.voxstream.core.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.voxstream.core.config.keys.ConfigKey;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.config.validation.ConfigValidators;
import com.voxstream.core.dao.ConfigDao;

/**
 * Provides typed access to configuration values with simple validation and
 * supports schema migrations tracked by config.schema.version.
 */
@Service
public class ConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final ConfigDao dao;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public ConfigurationService(ConfigDao dao) {
        this.dao = dao;
        runMigrations();
    }

    public <T> T get(ConfigKey<T> key) {
        Objects.requireNonNull(key);
        return key.getType().cast(cache.computeIfAbsent(key.getName(), n -> loadOrDefault(key)));
    }

    public <T> void set(ConfigKey<T> key, T value) {
        Objects.requireNonNull(key);
        validate(key, value);
        dao.put(key.getName(), serialize(value));
        cache.put(key.getName(), value);
    }

    public <T> void reset(ConfigKey<T> key) {
        set(key, key.getDefaultValue());
    }

    // Batch update with composite validation
    public void setAll(Map<ConfigKey<?>, Object> values) {
        // First run individual validation
        for (Map.Entry<ConfigKey<?>, Object> e : values.entrySet()) {
            @SuppressWarnings("unchecked")
            ConfigKey<Object> k = (ConfigKey<Object>) e.getKey();
            validate(k, e.getValue());
        }
        // Build snapshot map for composite validation
        Map<ConfigKey<?>, Object> snapshot = new HashMap<>();
        // Include existing cached plus new values (new takes precedence)
        for (var key : CoreConfigKeys.ALL) {
            Object val = values.entrySet().stream().filter(en -> en.getKey() == key).map(Map.Entry::getValue)
                    .findFirst().orElse(getUnchecked(key));
            snapshot.put(key, val);
        }
        ConfigValidators.validateComposite(snapshot);
        // If all good persist
        for (Map.Entry<ConfigKey<?>, Object> e : values.entrySet()) {
            dao.put(e.getKey().getName(), serialize(e.getValue()));
            cache.put(e.getKey().getName(), e.getValue());
        }
    }

    // Exposed for profile service to capture raw values without validation
    public Object getUncheckedForProfile(ConfigKey<?> key) {
        return getUnchecked(key);
    }

    // Convenience: current config snapshot (excluding internal filtering done by
    // caller)
    public Map<ConfigKey<?>, Object> getAllCurrent() {
        Map<ConfigKey<?>, Object> map = new HashMap<>();
        for (var k : CoreConfigKeys.ALL) {
            map.put(k, get(k));
        }
        return map;
    }

    private Object getUnchecked(ConfigKey<?> key) {
        return cache.computeIfAbsent(key.getName(), n -> loadOrDefaultUnchecked(key));
    }

    private <T> Object loadOrDefault(ConfigKey<T> key) {
        Optional<String> stored = dao.get(key.getName());
        if (stored.isPresent()) {
            try {
                return deserialize(stored.get(), key.getType());
            } catch (Exception e) {
                log.warn("Failed to parse config {} reverting to default: {}", key.getName(), e.getMessage());
            }
        }
        return key.getDefaultValue();
    }

    private Object loadOrDefaultUnchecked(ConfigKey<?> key) {
        Optional<String> stored = dao.get(key.getName());
        if (stored.isPresent()) {
            try {
                return deserialize(stored.get(), key.getType());
            } catch (Exception e) {
                log.warn("Failed to parse config {} reverting to default: {}", key.getName(), e.getMessage());
            }
        }
        return key.getDefaultValue();
    }

    private <T> void validate(ConfigKey<T> key, Object value) {
        // Basic null & type checks
        if (value == null)
            throw new IllegalArgumentException("Value for " + key.getName() + " cannot be null");
        if (!key.getType().isInstance(value))
            throw new IllegalArgumentException("Incorrect type for " + key.getName());
        if (value instanceof Number) {
            long lv = ((Number) value).longValue();
            // Allow -1 for specific sentinel-allowed keys
            boolean allowNegativeOne = (key == CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS && lv == -1);
            if (lv < 0 && !allowNegativeOne)
                throw new IllegalArgumentException("Negative value for " + key.getName());
        }
        // Extended validation
        ConfigKey<T> cast = (ConfigKey<T>) key;
        ConfigValidators.validate(cast, cast.getType().cast(value));
    }

    private String serialize(Object v) {
        if (v instanceof Instant)
            return Long.toString(((Instant) v).toEpochMilli());
        return v.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(String raw, Class<T> type) {
        if (type == String.class)
            return (T) raw;
        if (type == Integer.class)
            return (T) Integer.valueOf(raw);
        if (type == Long.class)
            return (T) Long.valueOf(raw);
        if (type == Boolean.class)
            return (T) Boolean.valueOf(raw);
        if (type == Instant.class)
            return (T) Instant.ofEpochMilli(Long.parseLong(raw));
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    // --- Migration logic ---
    private void runMigrations() {
        int current = dao.get(CoreConfigKeys.CONFIG_SCHEMA_VERSION.getName())
                .map(Integer::parseInt)
                .orElse(0); // 0 means fresh install (pre-versioning)
        int target = CoreConfigKeys.CONFIG_SCHEMA_VERSION.getDefaultValue();
        if (current == 0) {
            // baseline: set version to 1 (initial schema) without changes
            dao.put(CoreConfigKeys.CONFIG_SCHEMA_VERSION.getName(), String.valueOf(target));
            log.info("Configuration schema baseline applied at version {}", target);
            return;
        }
        if (current > target) {
            log.warn("Config schema version {} is ahead of code baseline {}. Consider upgrading code.", current,
                    target);
            return;
        }
        int working = current;
        while (working < target) {
            int next = working + 1;
            applyMigration(working, next);
            working = next;
            dao.put(CoreConfigKeys.CONFIG_SCHEMA_VERSION.getName(), String.valueOf(working));
            log.info("Config schema migrated to version {}", working);
        }
    }

    private void applyMigration(int from, int to) {
        // future migrations handled via switch
        switch (to) {
            case 1:
                // initial baseline handled separately
                break;
            default:
                log.debug("No migration actions for target version {}", to);
        }
    }

    public boolean getDynamicBoolean(String key, boolean defaultVal) {
        // Direct DAO lookup bypassing validators / schema; cached after first read
        Object cached = cache.get(key);
        if (cached instanceof Boolean b) {
            return b;
        }
        Optional<String> stored = dao.get(key);
        boolean val = stored.map(String::trim).filter(s -> !s.isEmpty()).map(Boolean::parseBoolean).orElse(defaultVal);
        cache.put(key, val);
        return val;
    }

    /**
     * Store a dynamic boolean key (e.g. platform.<id>.enabled) that is not part of
     * the static {@link CoreConfigKeys} registry. Bypasses validation layer.
     */
    public void setDynamicBoolean(String key, boolean value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Dynamic key cannot be null/blank");
        }
        dao.put(key, Boolean.toString(value));
        cache.put(key, value);
    }
}
