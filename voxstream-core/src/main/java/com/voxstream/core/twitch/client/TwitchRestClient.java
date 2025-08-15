package com.voxstream.core.twitch.client;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.twitch.model.TwitchOAuthToken;
import com.voxstream.core.twitch.oauth.TwitchOAuthService;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Minimal asynchronous Twitch REST client using OkHttp.
 * Only implements endpoints required for Phase 3.2 (token validation, users).
 */
@Component
public class TwitchRestClient implements Closeable {

    private static final String DEFAULT_HELIX = "https://api.twitch.tv/helix";
    private static final String DEFAULT_VALIDATE = "https://id.twitch.tv/oauth2/validate";

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final TwitchOAuthService oauthService;
    private final ConfigurationService config;
    private final String helixBase; // resolved once at construction (system property override)
    private final String validateUrl; // resolved once at construction
    private final AtomicReference<RateLimitInfo> lastRateLimitInfo = new AtomicReference<>();

    public TwitchRestClient(TwitchOAuthService oauthService, ConfigurationService config) {
        this.oauthService = Objects.requireNonNull(oauthService);
        this.config = Objects.requireNonNull(config);
        this.http = new OkHttpClient.Builder().build();
        this.mapper = new ObjectMapper();
        String apiBaseProp = System.getProperty("twitch.api.base", DEFAULT_HELIX);
        String idBaseProp = System.getProperty("twitch.id.base",
                DEFAULT_VALIDATE.substring(0, DEFAULT_VALIDATE.indexOf("/oauth2/validate")) + "/oauth2");
        // Normalize (strip trailing slashes)
        if (apiBaseProp.endsWith("/"))
            apiBaseProp = apiBaseProp.substring(0, apiBaseProp.length() - 1);
        if (idBaseProp.endsWith("/"))
            idBaseProp = idBaseProp.substring(0, idBaseProp.length() - 1);
        this.helixBase = apiBaseProp.contains("/helix") ? apiBaseProp
                : apiBaseProp + (apiBaseProp.endsWith("helix") ? "" : "/helix");
        // validate endpoint full URL
        this.validateUrl = (System.getProperty("twitch.id.base.validate") != null)
                ? System.getProperty("twitch.id.base.validate")
                : (idBaseProp.startsWith("http") ? idBaseProp.replace("/oauth2", "") : idBaseProp).replaceAll("/$", "")
                        + "/oauth2/validate";
    }

    public CompletableFuture<ValidationResult> validateToken(String accessToken) {
        Request req = new Request.Builder().url(validateUrl)
                .header("Authorization", "OAuth " + accessToken)
                .get().build();
        CompletableFuture<ValidationResult> fut = new CompletableFuture<>();
        http.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                fut.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    captureRateLimitHeaders(response);
                    if (!response.isSuccessful()) {
                        fut.complete(new ValidationResult(false, 0, null, null, null));
                        return;
                    }
                    JsonNode node = mapper.readTree(response.body().byteStream());
                    int expiresIn = node.path("expires_in").asInt(0);
                    String clientId = node.path("client_id").asText(null);
                    String login = node.path("login").asText(null);
                    String userId = node.path("user_id").asText(null);
                    fut.complete(new ValidationResult(true, expiresIn, clientId, login, userId));
                } catch (Exception ex) {
                    fut.completeExceptionally(ex);
                }
            }
        });
        return fut;
    }

    public CompletableFuture<Optional<User>> getUserById(String userId) {
        return authorizedGet("/users?id=" + userId).thenApply(opt -> opt.map(this::parseUserData));
    }

    private User parseUserData(JsonNode root) {
        JsonNode arr = root.path("data");
        if (arr.isArray() && arr.size() > 0) {
            JsonNode u = arr.get(0);
            return new User(u.path("id").asText(), u.path("login").asText(), u.path("display_name").asText());
        }
        return null;
    }

    private CompletableFuture<Optional<JsonNode>> authorizedGet(String path) {
        Optional<TwitchOAuthToken> tok = oauthService.getCachedToken();
        if (tok.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Map<String, String> hdrs = Map.of(
                "Authorization", "Bearer " + tok.get().getAccessToken(),
                "Client-Id", config.get(CoreConfigKeys.TWITCH_CLIENT_ID));
        Request req = new Request.Builder().url(helixBase + path).headers(Headers.of(hdrs)).get().build();
        CompletableFuture<Optional<JsonNode>> fut = new CompletableFuture<>();
        http.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                fut.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    captureRateLimitHeaders(response);
                    if (!response.isSuccessful()) {
                        fut.complete(Optional.empty());
                        return;
                    }
                    JsonNode node = mapper.readTree(response.body().byteStream());
                    fut.complete(Optional.ofNullable(node));
                } catch (Exception ex) {
                    fut.completeExceptionally(ex);
                }
            }
        });
        return fut;
    }

    private void captureRateLimitHeaders(Response response) {
        try {
            String limit = header(response, "Ratelimit-Limit", "RateLimit-Limit");
            String remaining = header(response, "Ratelimit-Remaining", "RateLimit-Remaining");
            String reset = header(response, "Ratelimit-Reset", "RateLimit-Reset");
            if (limit != null || remaining != null || reset != null) {
                Integer lim = parseInt(limit);
                Integer rem = parseInt(remaining);
                Long resetEpoch = parseLong(reset);
                lastRateLimitInfo.set(new RateLimitInfo(lim, rem, resetEpoch));
            }
        } catch (Exception ignore) {
        }
    }

    private Integer parseInt(String v) {
        try {
            return v == null ? null : Integer.parseInt(v);
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String v) {
        try {
            return v == null ? null : Long.parseLong(v);
        } catch (Exception e) {
            return null;
        }
    }

    private String header(Response r, String... names) {
        for (String n : names) {
            String val = r.header(n);
            if (val != null)
                return val;
        }
        return null;
    }

    public Optional<RateLimitInfo> getLastRateLimitInfo() {
        return Optional.ofNullable(lastRateLimitInfo.get());
    }

    @Override
    public void close() throws IOException {
        /* underlying OkHttp client intentionally kept for app lifetime */ }

    public record ValidationResult(boolean valid, int expiresInSec, String clientId, String login, String userId) {
    }

    public record User(String id, String login, String displayName) {
    }

    public static class RateLimitInfo { // extracted from headers (if present)
        public final Integer limit;
        public final Integer remaining;
        public final Long resetEpochSeconds;

        public RateLimitInfo(Integer limit, Integer remaining, Long resetEpochSeconds) {
            this.limit = limit;
            this.remaining = remaining;
            this.resetEpochSeconds = resetEpochSeconds;
        }

        public Instant resetTime() {
            return resetEpochSeconds == null ? null : Instant.ofEpochSecond(resetEpochSeconds);
        }

        @Override
        public String toString() {
            return "RateLimitInfo{" + "limit=" + limit + ", remaining=" + remaining + ", reset=" + resetEpochSeconds
                    + '}';
        }
    }
}
