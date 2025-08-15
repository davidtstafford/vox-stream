package com.voxstream.core.dao;

import java.util.Optional;

import com.voxstream.core.twitch.model.TwitchOAuthToken;

public interface TwitchOAuthTokenDao {
    Optional<TwitchOAuthToken> load();

    void save(TwitchOAuthToken token);

    void delete();
}
