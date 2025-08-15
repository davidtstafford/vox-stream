package com.voxstream.core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.security.EncryptionService;

public class JdbcConfigDaoTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcConfigDao dao;

    @BeforeEach
    void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa",
                "");
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute(
                "CREATE TABLE app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS twitch_oauth_token (id INT PRIMARY KEY, access_token CLOB, refresh_token CLOB, scopes CLOB, expires_at BIGINT, user_id VARCHAR(64), login VARCHAR(64), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        dao = new JdbcConfigDao(jdbcTemplate, new EncryptionService());
    }

    @Test
    void testPutAndGet() {
        dao.put("sample.key", "value");
        assertEquals("value", dao.get("sample.key").orElse(null));
    }

    @Test
    void testEncryption() {
        dao.put("my.secret.value", "secret");
        String stored = jdbcTemplate.queryForObject("SELECT cfg_value FROM app_config WHERE cfg_key='my.secret.value'",
                String.class);
        assertNotNull(stored);
        assertTrue(stored.startsWith("ENC:")); // Adjusted to match EncryptionService prefix (single colon)
        assertEquals("secret", dao.get("my.secret.value").orElse(null));
    }

    @Test
    void testDelete() {
        dao.put("a", "1");
        dao.delete("a");
        assertTrue(dao.get("a").isEmpty());
    }

    @Test
    void testAllMasksSecrets() {
        dao.put("plain.key", "hello");
        dao.put("api.secret.key", "top");
        assertEquals("hello", dao.all().get("plain.key"));
        assertEquals("***", dao.all().get("api.secret.key"));
    }
}
