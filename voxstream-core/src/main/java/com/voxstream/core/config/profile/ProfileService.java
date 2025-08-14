package com.voxstream.core.config.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.ConfigKey;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.ConfigDao;

/**
 * Manages saving, loading and applying named configuration profiles.
 * Profiles are stored as JSON blobs in the configuration store under
 * config.profile.<name>
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private static final String PROFILE_KEY_PREFIX = "config.profile.";

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]{1,40}");

    // Internal / non-exportable keys (not persisted inside profile)
    private static final Set<ConfigKey<?>> INTERNAL_KEYS = Set.of(
            CoreConfigKeys.CONFIG_SCHEMA_VERSION,
            CoreConfigKeys.LAST_EXPORT_HASH,
            CoreConfigKeys.DEFAULT_PROFILE_NAME);

    private final ConfigDao dao;
    private final ConfigurationService configurationService;

    public ProfileService(ConfigDao dao, ConfigurationService configurationService) {
        this.dao = dao;
        this.configurationService = configurationService;
        applyDefaultProfileIfConfigured();
    }

    /**
     * Save current live configuration values to a named profile.
     */
    public void saveProfile(String name) {
        saveProfile(name, snapshotCurrentValues());
    }

    /**
     * Save provided map of key->value under profile name.
     */
    public void saveProfile(String name, Map<ConfigKey<?>, Object> values) {
        validateName(name);
        Map<String, Object> exportable = new HashMap<>();
        for (var k : CoreConfigKeys.ALL) {
            if (INTERNAL_KEYS.contains(k))
                continue;
            Object v = values.getOrDefault(k, configurationService.getUncheckedForProfile(k));
            if (v != null) {
                exportable.put(k.getName(), v);
            }
        }
        String json = toJson(exportable);
        dao.put(PROFILE_KEY_PREFIX + name, json);
        log.info("Saved profile '{}' ({} keys)", name, exportable.size());
    }

    /**
     * List available profile names.
     */
    public List<String> listProfiles() {
        return dao.all().keySet().stream()
                .filter(k -> k.startsWith(PROFILE_KEY_PREFIX))
                .map(k -> k.substring(PROFILE_KEY_PREFIX.length()))
                .sorted().collect(Collectors.toList());
    }

    /**
     * Load profile values (raw map keyName->Object). Returns empty map if not
     * found.
     */
    public Map<String, Object> loadProfileRaw(String name) {
        validateName(name);
        return dao.get(PROFILE_KEY_PREFIX + name)
                .map(this::parseJson)
                .orElseGet(HashMap::new);
    }

    /**
     * Apply a profile to current configuration (batch validation & persistence).
     */
    public void applyProfile(String name) {
        Map<String, Object> raw = loadProfileRaw(name);
        if (raw.isEmpty()) {
            log.warn("Profile '{}' not found or empty", name);
            return;
        }
        Map<ConfigKey<?>, Object> updates = new HashMap<>();
        for (var entry : raw.entrySet()) {
            ConfigKey<?> key = (ConfigKey<?>) CoreConfigKeys.byName(entry.getKey());
            if (key == null)
                continue; // skip unknown (forward compatibility)
            if (INTERNAL_KEYS.contains(key))
                continue;
            Object val = castValue(entry.getValue(), key.getType());
            updates.put(key, val);
        }
        configurationService.setAll(updates);
        log.info("Applied profile '{}' ({} keys)", name, updates.size());
    }

    /**
     * Delete a stored profile.
     */
    public void deleteProfile(String name) {
        validateName(name);
        dao.delete(PROFILE_KEY_PREFIX + name);
        log.info("Deleted profile '{}'", name);
    }

    /**
     * Set the default profile (only stores the name). Profile is not applied until
     * next startup or explicit applyDefaultProfileIfConfigured().
     */
    public void setDefaultProfile(String name) {
        if (name == null || name.isBlank()) {
            configurationService.set(CoreConfigKeys.DEFAULT_PROFILE_NAME, "");
            return;
        }
        validateName(name);
        configurationService.set(CoreConfigKeys.DEFAULT_PROFILE_NAME, name);
    }

    public String getDefaultProfile() {
        return configurationService.get(CoreConfigKeys.DEFAULT_PROFILE_NAME);
    }

    /**
     * If default profile configured apply it (called at service construction).
     */
    public void applyDefaultProfileIfConfigured() {
        String def = configurationService.get(CoreConfigKeys.DEFAULT_PROFILE_NAME);
        if (def != null && !def.isBlank()) {
            log.info("Applying default profile '{}'", def);
            try {
                applyProfile(def);
            } catch (Exception ex) {
                log.warn("Failed to apply default profile '{}': {}", def, ex.getMessage());
            }
        }
    }

    // --- Helpers ---

    private Map<ConfigKey<?>, Object> snapshotCurrentValues() {
        Map<ConfigKey<?>, Object> map = new HashMap<>();
        for (var k : CoreConfigKeys.ALL) {
            if (INTERNAL_KEYS.contains(k))
                continue;
            map.put(k, configurationService.getUncheckedForProfile(k));
        }
        return map;
    }

    private void validateName(String name) {
        Objects.requireNonNull(name, "profile name");
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid profile name");
        }
    }

    private String toJson(Map<String, Object> map) {
        return map.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + formatValue(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String formatValue(Object v) {
        if (v instanceof Number || v instanceof Boolean)
            return v.toString();
        return "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        String s = json.trim();
        if (s.isEmpty() || s.equals("{}"))
            return result;
        if (s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}')
            throw new IllegalArgumentException("Invalid JSON");
        String body = s.substring(1, s.length() - 1).trim();
        if (body.isEmpty())
            return result;
        int idx = 0;
        boolean inStr = false;
        StringBuilder token = new StringBuilder();
        java.util.List<String> parts = new java.util.ArrayList<>();
        while (idx < body.length()) {
            char c = body.charAt(idx);
            if (c == '"' && (idx == 0 || body.charAt(idx - 1) != '\\'))
                inStr = !inStr;
            if (c == ',' && !inStr) {
                parts.add(token.toString());
                token.setLength(0);
            } else
                token.append(c);
            idx++;
        }
        parts.add(token.toString());
        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2)
                continue;
            String key = stripQuotes(kv[0].trim());
            String valRaw = kv[1].trim();
            Object val;
            if (valRaw.equalsIgnoreCase("true") || valRaw.equalsIgnoreCase("false")) {
                val = Boolean.valueOf(valRaw);
            } else if (valRaw.matches("-?\\d+")) { // integer
                try {
                    val = Integer.valueOf(valRaw);
                } catch (NumberFormatException nfe) {
                    val = Long.valueOf(valRaw);
                }
            } else if (valRaw.matches("-?\\d+\\.\\d+")) { // floating point
                val = Double.valueOf(valRaw);
            } else {
                val = stripQuotes(valRaw);
            }
            result.put(key, val);
        }
        return result;
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\""))
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        return s;
    }

    private Object castValue(Object raw, Class<?> type) {
        if (type == Integer.class && raw instanceof Number)
            return ((Number) raw).intValue();
        if (type == Double.class && raw instanceof Number)
            return ((Number) raw).doubleValue();
        if (type == Boolean.class && raw instanceof Boolean)
            return raw;
        if (type == String.class)
            return String.valueOf(raw);
        throw new IllegalArgumentException("Unsupported type in profile: " + type.getSimpleName());
    }

    public String checksumProfile(String name) {
        Map<String, Object> raw = loadProfileRaw(name);
        String json = toJson(raw.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
