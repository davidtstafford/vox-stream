package com.voxstream.core.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.voxstream.core.dao.ConfigDao;
import com.voxstream.core.security.EncryptionService;

@Repository
public class JdbcConfigDao implements ConfigDao {
    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    public JdbcConfigDao(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    private static final RowMapper<String> VALUE_MAPPER = new RowMapper<String>() {
        @Override
        public String mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            return rs.getString("cfg_value");
        }
    };

    @Override
    public void put(String key, String value) {
        boolean secure = key.toLowerCase().contains("secret") || key.toLowerCase().contains("token")
                || key.toLowerCase().contains("password");
        if (secure && value != null && !encryptionService.isEncrypted(value)) {
            value = encryptionService.encrypt(value);
        }
        jdbcTemplate.update("MERGE INTO app_config (cfg_key, cfg_value, updated_at) KEY(cfg_key) VALUES (?,?,?)", key,
                value, Timestamp.from(Instant.now()));
    }

    @Override
    public Optional<String> get(String key) {
        List<String> list = jdbcTemplate.query("SELECT cfg_value FROM app_config WHERE cfg_key=?", VALUE_MAPPER, key);
        return list.stream().findFirst().map(v -> encryptionService.isEncrypted(v) ? encryptionService.decrypt(v) : v);
    }

    @Override
    public void delete(String key) {
        jdbcTemplate.update("DELETE FROM app_config WHERE cfg_key=?", key);
    }

    @Override
    public Map<String, String> all() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT cfg_key, cfg_value FROM app_config");
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String raw = (String) r.get("cfg_value");
            String val = encryptionService.isEncrypted(raw) ? "***" : raw; // mask secrets in bulk listing
            result.put((String) r.get("cfg_key"), val);
        }
        return result;
    }
}
