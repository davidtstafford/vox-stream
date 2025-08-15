package com.voxstream.core.twitch.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.ConfigDao;
import com.voxstream.core.dao.TwitchOAuthTokenDao;
import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.dao.jdbc.JdbcTwitchOAuthTokenDao;
import com.voxstream.core.security.EncryptionService;
import com.voxstream.core.twitch.model.TwitchOAuthToken;
import com.voxstream.core.twitch.oauth.TwitchOAuthService;

/** Tests pre-request rate limit delay logic in TwitchRestClient (MVP). */
public class TwitchRestClientRateLimitTest {

    private HttpServer server;
    private int port;

    private TwitchRestClient restClient;

    @BeforeEach
    void setup() throws Exception {
        port = findFreePort();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.start();
        // Basic config + oauth service (token not required for validateToken path)
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
        cfg.set(CoreConfigKeys.TWITCH_SCOPES, "chat:read");
        cfg.set(CoreConfigKeys.TWITCH_REDIRECT_PORT, 8080);
        TwitchOAuthService oauth = new TwitchOAuthService(cfg, tokDao);
        oauth.start();
        // Seed token so authorizedGet (if used) would work; not strictly needed here.
        tokDao.save(new TwitchOAuthToken("access", "refresh", Set.of("chat:read"), Instant.now().plusSeconds(3600),
                "u1", "login", Instant.now()));
        // Point validate URL directly
        System.setProperty("twitch.id.base.validate", "http://localhost:" + port + "/oauth2/validate");
        restClient = new TwitchRestClient(oauth, cfg);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testPreRequestDelayApplied() throws Exception {
        long reset = Instant.now().getEpochSecond() + 2; // 2s window
        server.createContext("/oauth2/validate", new SequenceHandler(new ResponseSpec[] {
                // First response sets remaining=1 so next call must pause
                new ResponseSpec(200, jsonValidate().getBytes(),
                        new String[][] { { "RateLimit-Limit", "10" }, { "RateLimit-Remaining", "1" },
                                { "RateLimit-Reset", String.valueOf(reset) } }),
                // Second response (after sleep) normal
                new ResponseSpec(200, jsonValidate().getBytes(),
                        new String[][] { { "RateLimit-Limit", "10" }, { "RateLimit-Remaining", "8" },
                                { "RateLimit-Reset", String.valueOf(reset) } }) }));

        restClient.validateToken("token").join(); // prime rate limit info
        long start = System.currentTimeMillis();
        restClient.validateToken("token").join(); // should sleep ~2s
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 1700, "Expected >=1.7s delay, elapsed=" + elapsed);
    }

    @Test
    void testNoDelayWhenRemainingHigh() throws Exception {
        long reset = Instant.now().getEpochSecond() + 2;
        server.createContext("/oauth2/validate", new SequenceHandler(new ResponseSpec[] {
                new ResponseSpec(200, jsonValidate().getBytes(),
                        new String[][] { { "RateLimit-Limit", "10" }, { "RateLimit-Remaining", "5" },
                                { "RateLimit-Reset", String.valueOf(reset) } }),
                new ResponseSpec(200, jsonValidate().getBytes(),
                        new String[][] { { "RateLimit-Limit", "10" }, { "RateLimit-Remaining", "4" },
                                { "RateLimit-Reset", String.valueOf(reset) } }) }));

        restClient.validateToken("token").join(); // first
        long start = System.currentTimeMillis();
        restClient.validateToken("token").join(); // second (no sleep)
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 1000, "Expected no long delay, elapsed=" + elapsed);
    }

    @Test
    void testNoDelayWhenResetTooFar() throws Exception {
        long reset = Instant.now().getEpochSecond() + 120; // >30s threshold
        server.createContext("/oauth2/validate", new SequenceHandler(new ResponseSpec[] {
                new ResponseSpec(200, jsonValidate().getBytes(),
                        new String[][] { { "RateLimit-Limit", "10" }, { "RateLimit-Remaining", "1" },
                                { "RateLimit-Reset", String.valueOf(reset) } }),
                new ResponseSpec(200, jsonValidate().getBytes(),
                        new String[][] { { "RateLimit-Limit", "10" }, { "RateLimit-Remaining", "0" },
                                { "RateLimit-Reset", String.valueOf(reset) } }) }));

        restClient.validateToken("token").join();
        long start = System.currentTimeMillis();
        restClient.validateToken("token").join();
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 1000, "Expected no delay for long reset, elapsed=" + elapsed);
    }

    // --- Helpers ---

    private static int findFreePort() throws IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private class SequenceHandler implements HttpHandler {
        private final ResponseSpec[] specs;
        private int idx = 0;

        SequenceHandler(ResponseSpec[] specs) {
            this.specs = specs;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ResponseSpec spec = idx < specs.length ? specs[idx] : specs[specs.length - 1];
            if (idx < specs.length)
                idx++;
            for (String[] h : spec.headers) {
                exchange.getResponseHeaders().add(h[0], h[1]);
            }
            byte[] body = spec.body;
            exchange.sendResponseHeaders(spec.status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private static class ResponseSpec {
        final int status;
        final byte[] body;
        final String[][] headers;

        ResponseSpec(int status, byte[] body, String[][] headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }
    }

    private String jsonValidate() {
        return "{\"expires_in\":3600,\"client_id\":\"cid\",\"login\":\"user\",\"user_id\":\"u1\"}";
    }
}
