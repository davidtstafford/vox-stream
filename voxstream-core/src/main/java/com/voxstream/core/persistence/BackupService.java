package com.voxstream.core.persistence;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Provides simple backup & restore for the embedded H2 database. Backup uses H2
 * SCRIPT command
 * to produce a full DDL+DML SQL file. Restore executes that script after wiping
 * existing data.
 * NOTE: In future phases this can be extended for SQLite and compression.
 */
@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final JdbcTemplate jdbcTemplate;

    public BackupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("BackupService initialized (H2 mode)");
    }

    public Path backup(Path targetFile) {
        Objects.requireNonNull(targetFile, "targetFile");
        try (Connection c = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            Files.createDirectories(targetFile.getParent());
            String escaped = targetFile.toAbsolutePath().toString().replace("'", "''");
            jdbcTemplate.execute("SCRIPT TO '" + escaped + "'");
            log.info("Database backup written to {}", targetFile);
            return targetFile;
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Backup failed", e);
        }
    }

    public void restore(Path backupFile) {
        Objects.requireNonNull(backupFile, "backupFile");
        if (!Files.exists(backupFile)) {
            throw new IllegalArgumentException("Backup file does not exist: " + backupFile);
        }
        try {
            jdbcTemplate.execute("DROP ALL OBJECTS");
        } catch (Exception e) {
            log.warn("DROP ALL OBJECTS failed (continuing): {}", e.getMessage());
        }
        String sql;
        try {
            sql = Files.readString(backupFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (Connection c = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
                Statement st = c.createStatement()) {
            st.execute(sql);
            log.info("Database restored from {}", backupFile);
        } catch (SQLException e) {
            throw new RuntimeException("Restore failed", e);
        }
    }

    public Path backupToDirectory(Path dir, String filename) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return backup(dir.resolve(filename));
    }

    public void restore(File file) {
        restore(file.toPath());
    }
}
