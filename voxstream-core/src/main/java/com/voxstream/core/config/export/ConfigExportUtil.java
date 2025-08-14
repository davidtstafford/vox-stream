package com.voxstream.core.config.export;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.ConfigKey;
import com.voxstream.core.config.keys.CoreConfigKeys;

/**
 * Utility for producing deterministic JSON snapshots of configuration and
 * computing hashes for change detection. Lightweight and dependency free.
 */
public final class ConfigExportUtil {
    private ConfigExportUtil() {
    }

    /**
     * Build an ordered flat JSON object for all current config values.
     */
    public static String buildFullSnapshotJson(ConfigurationService svc) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (ConfigKey<?> k : CoreConfigKeys.ALL) {
            @SuppressWarnings("unchecked")
            ConfigKey<Object> cast = (ConfigKey<Object>) k;
            ordered.put(k.getName(), svc.get(cast));
        }
        return toJson(ordered);
    }

    /**
     * Serialize a flat map (key -> primitive/String) into JSON preserving insertion
     * order.
     */
    public static String toJson(Map<String, ?> map) {
        return map.entrySet().stream()
                .map(e -> quote(e.getKey()) + ":" + formatValue(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String formatValue(Object v) {
        if (v instanceof Number || v instanceof Boolean)
            return v.toString();
        return quote(String.valueOf(v));
    }

    /** Parse flat JSON object (string, number, boolean) into a map. */
    public static Map<String, Object> parseFlatJson(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (json == null)
            return result;
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
            if (valRaw.equalsIgnoreCase("true") || valRaw.equalsIgnoreCase("false"))
                val = Boolean.valueOf(valRaw);
            else if (valRaw.matches("-?\\d+"))
                val = Integer.valueOf(valRaw);
            else
                val = stripQuotes(valRaw);
            result.put(key, val);
        }
        return result;
    }

    private static String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\""))
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        return s;
    }

    public static String sha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
