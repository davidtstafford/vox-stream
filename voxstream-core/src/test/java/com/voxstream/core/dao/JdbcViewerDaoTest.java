package com.voxstream.core.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.voxstream.core.dao.jdbc.JdbcViewerDao;
import com.voxstream.core.model.Viewer;

public class JdbcViewerDaoTest {
    private JdbcTemplate jdbc;
    private JdbcViewerDao dao;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:viewer-test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);
        // Use IF NOT EXISTS so repeated test executions or multiple test methods do not
        // fail.
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS viewers (id VARCHAR(64) PRIMARY KEY, platform VARCHAR(32) NOT NULL, handle VARCHAR(128) NOT NULL, display_name VARCHAR(128), created_at TIMESTAMP NOT NULL, last_seen_at TIMESTAMP NOT NULL)");
        dao = new JdbcViewerDao(jdbc);
    }

    @Test
    void upsertAndFetch() {
        Viewer v = new Viewer(UUID.randomUUID().toString(), "TWITCH", "user123", "User123", Instant.now(),
                Instant.now());
        dao.upsert(v);
        assertTrue(dao.findById(v.getId()).isPresent());
        assertTrue(dao.findByPlatformHandle("TWITCH", "user123").isPresent());
    }

    @Test
    void touchUpdatesTimestamp() throws Exception {
        Instant created = Instant.now();
        Viewer v = new Viewer("id1", "TWITCH", "handle", "Handle", created, created);
        dao.upsert(v);
        Thread.sleep(5);
        Instant newTime = Instant.now();
        dao.touch("id1", newTime);
        Instant lastSeen = dao.findById("id1").get().getLastSeenAt();
        assertTrue(!lastSeen.isBefore(newTime));
    }
}
