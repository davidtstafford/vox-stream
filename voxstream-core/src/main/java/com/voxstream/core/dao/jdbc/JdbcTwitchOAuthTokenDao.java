package com.voxstream.core.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.voxstream.core.dao.TwitchOAuthTokenDao;
import com.voxstream.core.security.EncryptionService;
import com.voxstream.core.twitch.model.TwitchOAuthToken;

@Repository
public class JdbcTwitchOAuthTokenDao implements TwitchOAuthTokenDao {
    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    public JdbcTwitchOAuthTokenDao(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    private static final RowMapper<TwitchOAuthToken> MAPPER = new RowMapper<TwitchOAuthToken>() {
        @Override
        public TwitchOAuthToken mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            String access = rs.getString("access_token");
            String refresh = rs.getString("refresh_token");
            String scopesRaw = rs.getString("scopes");
            long expires = rs.getLong("expires_at");
            String userId = rs.getString("user_id");
            String login = rs.getString("login");
            Timestamp updated = rs.getTimestamp("updated_at");
            Set<String> scopes = scopesRaw == null || scopesRaw.isBlank() ? Set.of()
                    : Stream.of(scopesRaw.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
            return new TwitchOAuthToken(access, refresh, scopes, Instant.ofEpochMilli(expires), userId, login,
                    updated.toInstant());
        }
    };

    @Override
    public Optional<TwitchOAuthToken> load() {
        List<TwitchOAuthToken> list = jdbcTemplate.query("SELECT * FROM twitch_oauth_token LIMIT 1", MAPPER);
        if (list.isEmpty())
            return Optional.empty();
        TwitchOAuthToken tok = list.get(0);
        // decrypt tokens if encrypted
        String access = encryptionService.isEncrypted(tok.getAccessToken())
                ? encryptionService.decrypt(tok.getAccessToken())
                : tok.getAccessToken();
        String refresh = encryptionService.isEncrypted(tok.getRefreshToken())
                ? encryptionService.decrypt(tok.getRefreshToken())
                : tok.getRefreshToken();
        if (access != tok.getAccessToken() || refresh != tok.getRefreshToken()) {
            tok = new TwitchOAuthToken(access, refresh, tok.getScopes(), tok.getExpiresAt(), tok.getUserId(),
                    tok.getLogin(), tok.getUpdatedAt());
        }
        return Optional.of(tok);
    }

    @Override
    public void save(TwitchOAuthToken token) {
        String access = token.getAccessToken();
        String refresh = token.getRefreshToken();
        if (!encryptionService.isEncrypted(access))
            access = encryptionService.encrypt(access);
        if (!encryptionService.isEncrypted(refresh))
            refresh = encryptionService.encrypt(refresh);
        String scopes = String.join(",", token.getScopes());
        jdbcTemplate.update(
                "MERGE INTO twitch_oauth_token (id, access_token, refresh_token, scopes, expires_at, user_id, login, updated_at) KEY(id) VALUES (1,?,?,?,?,?,?,?)",
                access, refresh, scopes, token.getExpiresAt().toEpochMilli(), token.getUserId(), token.getLogin(),
                Timestamp.from(Instant.now()));
    }

    @Override
    public void delete() {
        jdbcTemplate.update("DELETE FROM twitch_oauth_token");
    }
}
