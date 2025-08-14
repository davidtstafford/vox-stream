package com.voxstream.core.config;

import java.time.Instant;
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

    private <T> void validate(ConfigKey<T> key, T value) {
        // Basic null & type checks
        if (value == null)
            throw new IllegalArgumentException("Value for " + key.getName() + " cannot be null");
        if (value instanceof Number) {
            if (((Number) value).longValue() < 0)
                throw new IllegalArgumentException("Negative value for " + key.getName());
        }
        // Extended validation
        ConfigValidators.validate(key, value);
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
}
