package com.voxstream.core.twitch.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a persisted Twitch OAuth token set (access + refresh + metadata).
 */
public class TwitchOAuthToken {
    private final String accessToken; // decrypted in-memory
    private final String refreshToken; // decrypted in-memory
    private final Set<String> scopes;
    private final Instant expiresAt; // absolute expiry time
    private final String userId; // broadcaster user id
    private final String login; // broadcaster login name
    private final Instant updatedAt;

    public TwitchOAuthToken(String accessToken, String refreshToken, Set<String> scopes, Instant expiresAt,
            String userId, String login, Instant updatedAt) {
        this.accessToken = Objects.requireNonNull(accessToken);
        this.refreshToken = Objects.requireNonNull(refreshToken);
        this.scopes = Objects.requireNonNull(scopes);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.userId = userId; // may be null until validated
        this.login = login; // may be null until validated
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getLogin() {
        return login;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt.minusSeconds(10));
    }

    public boolean willExpireWithin(long seconds) {
        return Instant.now().isAfter(expiresAt.minusSeconds(seconds));
    }
}
