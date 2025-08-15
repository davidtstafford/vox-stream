package com.voxstream.core.twitch.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.ConfigDao;
import com.voxstream.core.dao.TwitchOAuthTokenDao;
import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.dao.jdbc.JdbcTwitchOAuthTokenDao;
import com.voxstream.core.security.EncryptionService;
import com.voxstream.core.twitch.model.TwitchOAuthToken;

/** Placeholder for future WireMock tests (scaffolding only). */
public class TwitchOAuthServiceWireMockTest {

    private TwitchOAuthService service;
    private TwitchOAuthTokenDao tokDao;
    private ConfigurationService configService;

    @BeforeEach
    void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB, updated_at TIMESTAMP)");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS twitch_oauth_token (id INT PRIMARY KEY, access_token CLOB, refresh_token CLOB, scopes CLOB, expires_at BIGINT, user_id VARCHAR(64), login VARCHAR(64), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        ConfigDao configDao = new JdbcConfigDao(jdbc, new EncryptionService());
        tokDao = new JdbcTwitchOAuthTokenDao(jdbc, new EncryptionService());
        configService = new ConfigurationService(configDao);
        configService.set(CoreConfigKeys.TWITCH_ENABLED, true);
        configService.set(CoreConfigKeys.TWITCH_CLIENT_ID, "clientId");
        configService.set(CoreConfigKeys.TWITCH_CLIENT_SECRET, "secret");
        configService.set(CoreConfigKeys.TWITCH_SCOPES, "chat:read chat:edit");
        configService.set(CoreConfigKeys.TWITCH_REDIRECT_PORT, 8080);
        // Use default validation interval by not overriding (avoid invalid 0)
        service = new TwitchOAuthService(configService, tokDao);
        service.start();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void testEnsureTokenInteractiveNoToken() {
        assertTrue(service.ensureTokenInteractive().isEmpty(),
                "No token should be present since interactive flow cannot run in test without browser interaction");
    }

    @Test
    void testCachedTokenRoundTripManualInsert() {
        TwitchOAuthToken tok = new TwitchOAuthToken("access", "refresh", Set.of("chat:read"),
                Instant.now().plusSeconds(3600), "u1", "login", Instant.now());
        tokDao.save(tok);
        Optional<TwitchOAuthToken> loaded = tokDao.load();
        assertTrue(loaded.isPresent());
        assertEquals("access", loaded.get().getAccessToken());
    }
}
