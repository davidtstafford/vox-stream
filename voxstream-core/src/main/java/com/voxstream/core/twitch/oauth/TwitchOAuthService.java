package com.voxstream.core.twitch.oauth;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.TwitchOAuthTokenDao;
import com.voxstream.core.twitch.model.TwitchOAuthToken;

/**
 * Handles Twitch OAuth login, refresh, and persistence.
 * Minimal MVP implementation (Phase 3.2) using java.net.http.
 */
@Service
public class TwitchOAuthService {
    private static final Logger log = LoggerFactory.getLogger(TwitchOAuthService.class);

    private final ConfigurationService config;
    private final TwitchOAuthTokenDao tokenDao;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "twitch-oauth");
        t.setDaemon(true);
        return t;
    });

    private volatile TwitchOAuthToken cached; // decrypted active token
    private volatile boolean started;
    private final CopyOnWriteArrayList<Consumer<TwitchOAuthToken>> tokenListeners = new CopyOnWriteArrayList<>();
    private volatile long lastValidationEpochMs = 0L;

    private final String idBase = System.getProperty("twitch.id.base", "https://id.twitch.tv");
    private final String apiBase = System.getProperty("twitch.api.base", "https://api.twitch.tv");

    public TwitchOAuthService(ConfigurationService config, TwitchOAuthTokenDao tokenDao) {
        this.config = config;
        this.tokenDao = tokenDao;
    }

    public synchronized void start() {
        if (started)
            return;
        started = true;
        if (!config.get(CoreConfigKeys.TWITCH_ENABLED)) {
            log.info("Twitch disabled - OAuth service not started");
            return;
        }
        cached = tokenDao.load().orElse(null);
        scheduleValidationTask();
    }

    public synchronized void shutdown() {
        scheduler.shutdownNow();
        started = false;
    }

    /** Ensure we have a valid (non-expired) token, launching login if necessary. */
    public synchronized Optional<TwitchOAuthToken> ensureTokenInteractive() {
        if (!config.get(CoreConfigKeys.TWITCH_ENABLED))
            return Optional.empty();
        // New: attempt lazy reload from DAO if cache empty (supports tests or external
        // seeding)
        if (cached == null) {
            tokenDao.load().ifPresent(t -> {
                cached = t;
                log.debug("Loaded Twitch OAuth token from DAO on-demand (lazy reload)");
            });
        }
        if (cached == null) {
            log.info("No Twitch OAuth token present - initiating login");
            performInteractiveLogin();
        } else if (cached.willExpireWithin(300)) { // refresh if less than 5 min remaining
            tryRefresh();
        }
        return Optional.ofNullable(cached);
    }

    public Optional<TwitchOAuthToken> getCachedToken() {
        return Optional.ofNullable(cached);
    }

    public void addTokenListener(Consumer<TwitchOAuthToken> listener) {
        if (listener != null)
            tokenListeners.add(listener);
    }

    private void notifyTokenListeners() {
        TwitchOAuthToken c = cached;
        if (c != null)
            tokenListeners.forEach(l -> {
                try {
                    l.accept(c);
                } catch (Exception ignored) {
                }
            });
    }

    private void scheduleValidationTask() {
        int interval = config.get(CoreConfigKeys.TWITCH_TOKEN_VALIDATION_INTERVAL_SEC);
        if (interval <= 0)
            return;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (cached == null)
                    return;
                // Always validate/augment (skipping if validated in last 30s to avoid
                // hammering)
                long now = System.currentTimeMillis();
                if (now - lastValidationEpochMs > 30_000) {
                    validateAndAugmentToken();
                }
                if (cached != null && cached.willExpireWithin(600)) { // 10 min window
                    log.info("Twitch token nearing expiry -> refreshing");
                    tryRefresh();
                }
            } catch (Exception e) {
                log.warn("Periodic token validation failed: {}", e.toString());
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private void performInteractiveLogin() {
        if (Boolean.getBoolean("twitch.oauth.disableInteractive")) {
            log.info(
                    "Interactive Twitch OAuth suppressed by system property twitch.oauth.disableInteractive=true (test mode)");
            return; // test hook
        }
        String clientId = config.get(CoreConfigKeys.TWITCH_CLIENT_ID);
        String clientSecret = config.get(CoreConfigKeys.TWITCH_CLIENT_SECRET);
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.warn("Cannot start Twitch OAuth login - clientId/secret missing");
            return;
        }
        int port = config.get(CoreConfigKeys.TWITCH_REDIRECT_PORT);
        String state = UUID.randomUUID().toString();
        String scopesJoined = config.get(CoreConfigKeys.TWITCH_SCOPES);
        String scopeParam = encode(scopesJoined.replace(',', ' ')).replace("+", "%20");
        String redirectUri = "http://localhost:" + port + "/callback"; // loopback HTTP
        // Use idBase for auth URL (strip possible trailing /)
        String base = idBase.endsWith("/") ? idBase.substring(0, idBase.length() - 1) : idBase;
        String authUrl = base + "/oauth2/authorize?response_type=code&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri) + "&scope=" + scopeParam + "&state=" + state;
        log.info("Open this URL in a browser to authorize Twitch: {}", authUrl);
        startLoopbackServer(port, state, redirectUri, clientId, clientSecret);
        try {
            java.awt.Desktop d = java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
            if (d != null && d.isSupported(java.awt.Desktop.Action.BROWSE)) {
                d.browse(URI.create(authUrl));
            }
        } catch (Exception ignored) {
        }
    }

    private void startLoopbackServer(int port, String expectedState, String redirectUri, String clientId,
            String clientSecret) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/callback", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String query = exchange.getRequestURI().getQuery();
                    String responseMsg;
                    if (query == null) {
                        responseMsg = "Missing query";
                    } else {
                        var params = parseQuery(query);
                        if (!expectedState.equals(params.get("state"))) {
                            responseMsg = "State mismatch";
                        } else if (params.containsKey("error")) {
                            responseMsg = "Error: " + params.get("error");
                        } else {
                            String code = params.get("code");
                            try {
                                exchangeCodeForToken(code, redirectUri, clientId, clientSecret);
                                responseMsg = "Twitch authorization complete. You may close this window.";
                            } catch (Exception e) {
                                log.error("Token exchange failed", e);
                                responseMsg = "Token exchange failed: " + e.getMessage();
                            }
                        }
                    }
                    byte[] bytes = responseMsg.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    scheduler.schedule(() -> server.stop(0), 2, TimeUnit.SECONDS);
                }
            });
            new Thread(server::start, "twitch-oauth-loopback").start();
        } catch (IOException e) {
            log.error("Failed to start loopback server on port {}: {}", port, e.getMessage());
        }
    }

    private void exchangeCodeForToken(String code, String redirectUri, String clientId, String clientSecret)
            throws Exception {
        String body = "client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code) + "&grant_type=authorization_code&redirect_uri=" + encode(redirectUri);
        String base = idBase.endsWith("/") ? idBase.substring(0, idBase.length() - 1) : idBase;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "Token exchange failed status=" + resp.statusCode() + " body=" + resp.body());
        }
        JsonNode json = mapper.readTree(resp.body());
        String access = json.get("access_token").asText();
        String refresh = json.get("refresh_token").asText();
        long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;
        Set<String> scopes = new HashSet<>();
        if (json.has("scope")) {
            json.get("scope").forEach(n -> scopes.add(n.asText()));
        }
        // Save token (no user info yet; to be populated after validation call)
        TwitchOAuthToken tok = new TwitchOAuthToken(access, refresh, scopes, Instant.now().plusSeconds(expiresIn), null,
                null, Instant.now());
        tokenDao.save(tok);
        cached = tok;
        log.info("Twitch OAuth token stored (scopes={} expiresIn={}s)", scopes, expiresIn);
        // Populate user id/login & adjust expiry using validate endpoint
        validateAndAugmentToken();
    }

    private void tryRefresh() {
        if (cached == null)
            return;
        String clientId = config.get(CoreConfigKeys.TWITCH_CLIENT_ID);
        String clientSecret = config.get(CoreConfigKeys.TWITCH_CLIENT_SECRET);
        if (clientId.isBlank() || clientSecret.isBlank())
            return;
        String body = "grant_type=refresh_token&refresh_token=" + encode(cached.getRefreshToken()) + "&client_id="
                + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        String base = idBase.endsWith("/") ? idBase.substring(0, idBase.length() - 1) : idBase;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, ex) -> {
            if (ex != null) {
                log.warn("Refresh request failed: {}", ex.toString());
                return;
            }
            if (resp.statusCode() / 100 != 2) {
                if (resp.statusCode() == 400 || resp.statusCode() == 401) {
                    log.warn("Refresh indicates invalid token (status={}) -> deleting & re-login", resp.statusCode());
                    tokenDao.delete();
                    cached = null;
                    performInteractiveLogin();
                } else {
                    log.warn("Refresh failed status={} body={}", resp.statusCode(), resp.body());
                }
                return;
            }
            try {
                JsonNode json = mapper.readTree(resp.body());
                String access = json.get("access_token").asText();
                String refresh = json.get("refresh_token").asText();
                long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;
                Set<String> scopes = new HashSet<>(cached.getScopes());
                if (json.has("scope")) {
                    scopes.clear();
                    json.get("scope").forEach(n -> scopes.add(n.asText()));
                }
                cached = new TwitchOAuthToken(access, refresh, scopes, Instant.now().plusSeconds(expiresIn),
                        cached.getUserId(), cached.getLogin(), Instant.now());
                tokenDao.save(cached);
                log.info("Twitch token refreshed; expires in {}s", expiresIn);
                notifyTokenListeners();
                // Augment with validation call (async)
                scheduler.execute(() -> {
                    try {
                        validateAndAugmentToken();
                    } catch (Exception ignored) {
                    }
                    ;
                });
            } catch (Exception parseEx) {
                log.warn("Failed to parse refresh response: {}", parseEx.toString());
            }
        });
    }

    private void validateAndAugmentToken() {
        TwitchOAuthToken current = cached;
        if (current == null)
            return;
        try {
            String base = idBase.endsWith("/") ? idBase.substring(0, idBase.length() - 1) : idBase;
            HttpRequest validateReq = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/oauth2/validate"))
                    .header("Authorization", "OAuth " + current.getAccessToken())
                    .GET().build();
            HttpResponse<String> validateResp = http.send(validateReq, HttpResponse.BodyHandlers.ofString());
            if (validateResp.statusCode() == 401) {
                log.warn("Twitch token invalid (401) -> deleting & initiating re-login");
                tokenDao.delete();
                cached = null;
                performInteractiveLogin();
                return;
            }
            if (validateResp.statusCode() / 100 != 2) {
                log.warn("Token validation failed status={} body={}", validateResp.statusCode(), validateResp.body());
                return; // transient; keep token
            }
            lastValidationEpochMs = System.currentTimeMillis();
            JsonNode vJson = mapper.readTree(validateResp.body());
            String clientIdValidated = vJson.path("client_id").asText(null);
            String login = vJson.path("login").asText(null);
            String userId = vJson.path("user_id").asText(null);
            long expiresIn = vJson.path("expires_in").asLong(0);
            if (clientIdValidated != null && !clientIdValidated.isBlank()) {
                String configuredClientId = config.get(CoreConfigKeys.TWITCH_CLIENT_ID);
                if (!configuredClientId.isBlank() && !configuredClientId.equals(clientIdValidated)) {
                    log.warn(
                            "Validated client_id does not match configured twitch.clientId (configured={} validated={})",
                            configuredClientId, clientIdValidated);
                }
            }
            Instant newExpiry = expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : current.getExpiresAt();
            boolean changed = false;
            String newUserId = current.getUserId();
            String newLogin = current.getLogin();
            if ((userId != null && !userId.equals(current.getUserId()))
                    || (login != null && !login.equals(current.getLogin()))
                    || !newExpiry.equals(current.getExpiresAt())) {
                newUserId = userId != null ? userId : current.getUserId();
                newLogin = login != null ? login : current.getLogin();
                changed = true;
            }
            if ((newUserId == null || newLogin == null) && current.getAccessToken() != null) {
                try {
                    String api = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
                    HttpRequest userReq = HttpRequest.newBuilder()
                            .uri(URI.create(api + "/helix/users"))
                            .header("Authorization", "Bearer " + current.getAccessToken())
                            .header("Client-Id", config.get(CoreConfigKeys.TWITCH_CLIENT_ID))
                            .GET().build();
                    HttpResponse<String> userResp = http.send(userReq, HttpResponse.BodyHandlers.ofString());
                    if (userResp.statusCode() / 100 == 2) {
                        JsonNode uJson = mapper.readTree(userResp.body());
                        JsonNode dataArr = uJson.path("data");
                        if (dataArr.isArray() && dataArr.size() > 0) {
                            JsonNode u = dataArr.get(0);
                            String uid = u.path("id").asText(null);
                            String logName = u.path("login").asText(null);
                            if (uid != null && logName != null) {
                                if (!uid.equals(newUserId) || !logName.equals(newLogin))
                                    changed = true;
                                newUserId = uid;
                                newLogin = logName;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Helix users enrichment failed: {}", e.toString());
                }
            }
            if (changed) {
                cached = new TwitchOAuthToken(current.getAccessToken(), current.getRefreshToken(), current.getScopes(),
                        newExpiry, newUserId, newLogin, Instant.now());
                tokenDao.save(cached);
                log.info("Twitch token augmented (userId={} login={} expiresAt={})", newUserId, newLogin, newExpiry);
                notifyTokenListeners();
            }
        } catch (IOException | InterruptedException e) {
            log.debug("Token validation IO error: {}", e.toString());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Unexpected error validating token: {}", e.toString());
        }
    }

    /** Test hook to force immediate validation without waiting for scheduler. */
    public void runValidationNowForTest() {
        validateAndAugmentToken();
    }

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static java.util.Map<String, String> parseQuery(String q) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        Arrays.stream(q.split("&")).forEach(p -> {
            int idx = p.indexOf('=');
            if (idx > 0) {
                String k = urlDecode(p.substring(0, idx));
                String val = urlDecode(p.substring(idx + 1));
                m.put(k, val);
            }
        });
        return m;
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public synchronized void revokeAndDeleteTokens() {
        TwitchOAuthToken current = cached;
        cached = null; // optimistic clear
        try {
            if (current != null && current.getAccessToken() != null && !current.getAccessToken().isBlank()) {
                String clientId = config.get(CoreConfigKeys.TWITCH_CLIENT_ID);
                if (clientId != null && !clientId.isBlank()) {
                    String base = idBase.endsWith("/") ? idBase.substring(0, idBase.length() - 1) : idBase;
                    String body = "client_id=" + encode(clientId) + "&token=" + encode(current.getAccessToken());
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/oauth2/revoke"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    try {
                        http.send(req, HttpResponse.BodyHandlers.ofString()); // ignore response status (best effort)
                    } catch (Exception e) {
                        log.debug("Token revoke call failed (ignored): {}", e.toString());
                    }
                }
            }
        } finally {
            try {
                tokenDao.delete();
            } catch (Exception e) {
                log.debug("Failed to delete stored Twitch token: {}", e.toString());
            }
            notifyTokenListeners();
        }
    }
}
