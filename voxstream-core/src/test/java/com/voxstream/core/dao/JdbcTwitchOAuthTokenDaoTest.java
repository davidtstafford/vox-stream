package com.voxstream.core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.voxstream.core.dao.jdbc.JdbcTwitchOAuthTokenDao;
import com.voxstream.core.security.EncryptionService;
import com.voxstream.core.twitch.model.TwitchOAuthToken;

public class JdbcTwitchOAuthTokenDaoTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcTwitchOAuthTokenDao dao;

    @BeforeEach
    void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa",
                "");
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS twitch_oauth_token (id INT PRIMARY KEY, access_token CLOB, refresh_token CLOB, scopes CLOB, expires_at BIGINT, user_id VARCHAR(64), login VARCHAR(64), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        dao = new JdbcTwitchOAuthTokenDao(jdbcTemplate, new EncryptionService());
    }

    @Test
    void testSaveLoadRoundTrip() {
        TwitchOAuthToken token = new TwitchOAuthToken("access123", "refresh123", Set.of("chat:read", "chat:edit"),
                Instant.now().plusSeconds(3600), "12345", "mylogin", Instant.now());
        dao.save(token);
        TwitchOAuthToken loaded = dao.load().orElseThrow();
        assertEquals("access123", loaded.getAccessToken());
        assertEquals("refresh123", loaded.getRefreshToken());
        assertTrue(loaded.getScopes().contains("chat:read"));
        assertEquals("12345", loaded.getUserId());
    }

    @Test
    void testDelete() {
        TwitchOAuthToken token = new TwitchOAuthToken("access", "refresh", Set.of(), Instant.now().plusSeconds(10),
                null,
                null, Instant.now());
        dao.save(token);
        assertTrue(dao.load().isPresent());
        dao.delete();
        assertTrue(dao.load().isEmpty());
    }
}
