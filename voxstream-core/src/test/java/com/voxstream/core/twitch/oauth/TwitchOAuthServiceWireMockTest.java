package com.voxstream.core.twitch.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;
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

/**
 * Lightweight mock server tests for TwitchOAuthService validating token
 * augmentation, invalidation and refresh.
 * Replaces earlier WireMock approach to avoid Jetty dependency conflicts.
 */
public class TwitchOAuthServiceWireMockTest {

    private TwitchOAuthService service;
    private TwitchOAuthTokenDao tokDao;
    private ConfigurationService configService;
    private HttpServer server;
    private int port;

    @BeforeEach
    void setup() throws Exception {
        System.setProperty("twitch.oauth.disableInteractive", "true"); // suppress real interactive login
        startMockServer();
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
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
        System.clearProperty("twitch.id.base");
        System.clearProperty("twitch.api.base");
    }

    private void startService() {
        service = new TwitchOAuthService(configService, tokDao);
        service.start();
    }

    private void startMockServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        System.setProperty("twitch.id.base", "http://localhost:" + port);
        System.setProperty("twitch.api.base", "http://localhost:" + port);
        // Handlers initially no-op; individual tests will override by setting static
        // response vars
        server.createContext("/oauth2/validate", new StaticResponseHandler(() -> currentValidateResponse));
        server.createContext("/helix/users", new StaticResponseHandler(() -> currentUsersResponse));
        server.createContext("/oauth2/token", new StaticResponseHandler(() -> currentTokenResponse));
        new Thread(server::start, "mock-twitch-server").start();
    }

    // Simple holder of dynamic responses per endpoint
    private volatile HttpResponseData currentValidateResponse;
    private volatile HttpResponseData currentUsersResponse;
    private volatile HttpResponseData currentTokenResponse;

    private record HttpResponseData(int status, String body, String contentType) {
    }

    private static class StaticResponseHandler implements HttpHandler {
        private final java.util.function.Supplier<HttpResponseData> supplier;

        StaticResponseHandler(java.util.function.Supplier<HttpResponseData> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpResponseData data = supplier.get();
            if (data == null)
                data = new HttpResponseData(404, "", "text/plain");
            byte[] bytes = data.body() == null ? new byte[0] : data.body().getBytes();
            exchange.getResponseHeaders().add("Content-Type", data.contentType());
            exchange.sendResponseHeaders(data.status(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    @Test
    void testValidateAndAugmentTokenSuccess() {
        // Token without user info
        TwitchOAuthToken tok = new TwitchOAuthToken("access", "refresh", Set.of("chat:read"),
                Instant.now().plusSeconds(3600), null, null, Instant.now());
        tokDao.save(tok);
        currentValidateResponse = new HttpResponseData(200,
                "{\"client_id\":\"clientId\",\"login\":\"loginUser\",\"user_id\":\"u123\",\"expires_in\":1200}",
                "application/json");
        currentUsersResponse = new HttpResponseData(200, "{\"data\":[{\"id\":\"u123\",\"login\":\"loginUser\"}]}",
                "application/json");
        startService();
        service.runValidationNowForTest();
        Optional<TwitchOAuthToken> updated = service.getCachedToken();
        assertTrue(updated.isPresent());
        assertEquals("u123", updated.get().getUserId());
        assertEquals("loginUser", updated.get().getLogin());
    }

    @Test
    void testValidateTokenInvalidDeletesToken() {
        TwitchOAuthToken tok = new TwitchOAuthToken("badAccess", "refresh", Set.of("chat:read"),
                Instant.now().plusSeconds(3600), null, null, Instant.now());
        tokDao.save(tok);
        currentValidateResponse = new HttpResponseData(401, "", "text/plain");
        startService();
        service.runValidationNowForTest();
        assertTrue(service.getCachedToken().isEmpty(), "Token should be deleted after 401 validation");
    }

    @Test
    void testRefreshFlow() throws Exception {
        // Token expiring soon to trigger refresh
        TwitchOAuthToken tok = new TwitchOAuthToken("oldAccess", "oldRefresh", Set.of("chat:read"),
                Instant.now().plusSeconds(100), "u1", "login", Instant.now());
        tokDao.save(tok);
        currentValidateResponse = new HttpResponseData(200,
                "{\"client_id\":\"clientId\",\"login\":\"login\",\"user_id\":\"u1\",\"expires_in\":100}",
                "application/json");
        currentTokenResponse = new HttpResponseData(200,
                "{\"access_token\":\"newAccess\",\"refresh_token\":\"newRefresh\",\"expires_in\":3600,\"scope\":[\"chat:read\"]}",
                "application/json");
        startService();
        service.ensureTokenInteractive();
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) { // wait up to 3s
            Optional<TwitchOAuthToken> cur = service.getCachedToken();
            if (cur.isPresent() && "newAccess".equals(cur.get().getAccessToken())) {
                break;
            }
            Thread.sleep(50);
        }
        Optional<TwitchOAuthToken> refreshed = service.getCachedToken();
        assertTrue(refreshed.isPresent());
        assertEquals("newAccess", refreshed.get().getAccessToken());
        assertEquals("newRefresh", refreshed.get().getRefreshToken());
    }
}
