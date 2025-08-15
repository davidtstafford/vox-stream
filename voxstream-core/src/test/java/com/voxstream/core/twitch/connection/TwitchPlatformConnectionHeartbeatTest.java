package com.voxstream.core.twitch.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

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
import com.voxstream.core.twitch.eventsub.MockEventSubClient;
import com.voxstream.core.twitch.model.TwitchOAuthToken;
import com.voxstream.core.twitch.oauth.TwitchOAuthService;
import com.voxstream.platform.api.PlatformStatus;

/** Tests heartbeat timeout handling -> failed status then reconnect attempt. */
public class TwitchPlatformConnectionHeartbeatTest {

    private record Env(TwitchPlatformConnection conn, MockEventSubClient mock, TwitchOAuthService oauth,
            ConfigurationService cfg, TwitchOAuthTokenDao tokDao) {
    }

    private Env setupEnv() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB, updated_at TIMESTAMP)");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS twitch_oauth_token (id INT PRIMARY KEY, access_token CLOB, refresh_token CLOB, scopes CLOB, expires_at BIGINT, user_id VARCHAR(64), login VARCHAR(64), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        ConfigDao cfgDao = new JdbcConfigDao(jdbc, new EncryptionService());
        TwitchOAuthTokenDao tokDao = new JdbcTwitchOAuthTokenDao(jdbc, new EncryptionService());
        ConfigurationService cfg = new ConfigurationService(cfgDao);
        cfg.set(CoreConfigKeys.TWITCH_ENABLED, true);
        cfg.set(CoreConfigKeys.TWITCH_CLIENT_ID, "cid");
        cfg.set(CoreConfigKeys.TWITCH_CLIENT_SECRET, "secret");
        cfg.set(CoreConfigKeys.TWITCH_SCOPES, "chat:read chat:edit");
        cfg.set(CoreConfigKeys.TWITCH_REDIRECT_PORT, 8080);
        TwitchOAuthService oauth = new TwitchOAuthService(cfg, tokDao);
        oauth.start();
        // seed a token directly (bypassing interactive)
        TwitchOAuthToken tok = new TwitchOAuthToken("access", "refresh", Set.of("chat:read"),
                Instant.now().plusSeconds(3600), "u1", "login", Instant.now());
        tokDao.save(tok);
        var mock = new MockEventSubClient();
        TwitchPlatformConnection conn = new TwitchPlatformConnection(cfg, oauth, mock);
        return new Env(conn, mock, oauth, cfg, tokDao);
    }

    @Test
    void heartbeatTimeoutMarksFailedAndTriggersReconnect() throws Exception {
        Env e = setupEnv();
        assertTrue(e.conn.connect().get());
        assertEquals(1, e.mock.getConnectCount());
        assertEquals(PlatformStatus.State.CONNECTED, e.conn.status().state());
        // fire heartbeat timeout
        e.mock.fireHeartbeatTimeout();
        Thread.sleep(75); // allow async listener to run
        assertEquals(PlatformStatus.State.FAILED, e.conn.status().state());
        assertEquals("eventsub.heartbeat.timeout", e.conn.status().detail());
        // Wait for auto reconnect attempt (initial small delay ~200ms in
        // implementation)
        Thread.sleep(300);
        // After reconnect attempt, connect count should increase
        assertTrue(e.mock.getConnectCount() >= 2, "Expected reconnect attempt after heartbeat timeout");
    }
}
