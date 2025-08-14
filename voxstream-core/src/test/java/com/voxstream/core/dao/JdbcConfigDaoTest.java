package com.voxstream.core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.voxstream.core.dao.jdbc.JdbcConfigDao;

public class JdbcConfigDaoTest {
    private JdbcTemplate jdbc;
    private JdbcConfigDao dao;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:config-test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        dao = new JdbcConfigDao(jdbc);
    }

    @Test
    void putGetDelete() {
        dao.put("k1", "v1");
        assertEquals("v1", dao.get("k1").orElse(null));
        dao.put("k1", "v2");
        assertEquals("v2", dao.get("k1").orElse(null));
        dao.delete("k1");
        assertTrue(dao.get("k1").isEmpty());
    }
}
