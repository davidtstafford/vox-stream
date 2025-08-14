package com.voxstream.core.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.voxstream.core.dao.ConfigDao;

@Repository
public class JdbcConfigDao implements ConfigDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcConfigDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<String> VALUE_MAPPER = new RowMapper<String>() {
        @Override
        public String mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            return rs.getString("cfg_value");
        }
    };

    @Override
    public void put(String key, String value) {
        jdbcTemplate.update("MERGE INTO app_config (cfg_key, cfg_value, updated_at) KEY(cfg_key) VALUES (?,?,?)", key,
                value, Timestamp.from(Instant.now()));
    }

    @Override
    public Optional<String> get(String key) {
        List<String> list = jdbcTemplate.query("SELECT cfg_value FROM app_config WHERE cfg_key=?", VALUE_MAPPER, key);
        return list.stream().findFirst();
    }

    @Override
    public void delete(String key) {
        jdbcTemplate.update("DELETE FROM app_config WHERE cfg_key=?", key);
    }
}
