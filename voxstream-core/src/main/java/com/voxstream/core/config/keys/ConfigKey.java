package com.voxstream.core.config.keys;

/**
 * Represents a strongly-typed configuration key.
 */
public class ConfigKey<T> {
    private final String name;
    private final Class<T> type;
    private final T defaultValue;
    private final boolean secure;
    private final String description;

    public ConfigKey(String name, Class<T> type, T defaultValue, boolean secure, String description) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.secure = secure;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }
}
