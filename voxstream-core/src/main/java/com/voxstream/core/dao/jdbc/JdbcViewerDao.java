package com.voxstream.core.dao.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.voxstream.core.dao.ViewerDao;
import com.voxstream.core.model.Viewer;

@Repository
public class JdbcViewerDao implements ViewerDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcViewerDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Viewer> MAPPER = new RowMapper<Viewer>() {
        @Override
        public Viewer mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            return new Viewer(
                    rs.getString("id"),
                    rs.getString("platform"),
                    rs.getString("handle"),
                    rs.getString("display_name"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("last_seen_at").toInstant());
        }
    };

    @Override
    public void upsert(Viewer viewer) {
        jdbcTemplate.update(
                "MERGE INTO viewers (id, platform, handle, display_name, created_at, last_seen_at) KEY(id) VALUES (?,?,?,?,?,?)",
                viewer.getId(), viewer.getPlatform(), viewer.getHandle(), viewer.getDisplayName(),
                Timestamp.from(viewer.getCreatedAt()), Timestamp.from(viewer.getLastSeenAt()));
    }

    @Override
    public Optional<Viewer> findById(String id) {
        List<Viewer> list = jdbcTemplate.query("SELECT * FROM viewers WHERE id=?", MAPPER, id);
        return list.stream().findFirst();
    }

    @Override
    public Optional<Viewer> findByPlatformHandle(String platform, String handle) {
        List<Viewer> list = jdbcTemplate.query("SELECT * FROM viewers WHERE platform=? AND handle=?", MAPPER, platform,
                handle);
        return list.stream().findFirst();
    }

    @Override
    public List<Viewer> recent(int limit) {
        return jdbcTemplate.query("SELECT * FROM viewers ORDER BY last_seen_at DESC LIMIT ?", MAPPER, limit);
    }

    @Override
    public void touch(String id, Instant when) {
        jdbcTemplate.update("UPDATE viewers SET last_seen_at=? WHERE id=?", Timestamp.from(when), id);
    }
}
