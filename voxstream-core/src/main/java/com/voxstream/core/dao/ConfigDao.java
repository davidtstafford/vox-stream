package com.voxstream.core.dao;

import java.util.Map;
import java.util.Optional;

public interface ConfigDao {
    void put(String key, String value);

    Optional<String> get(String key);

    void delete(String key);

    Map<String, String> all();
}
