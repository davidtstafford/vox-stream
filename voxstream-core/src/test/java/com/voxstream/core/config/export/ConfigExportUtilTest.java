package com.voxstream.core.config.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.security.EncryptionService;

class ConfigExportUtilTest {

    private ConfigurationService configService;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:export-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        EncryptionService enc = new EncryptionService();
        JdbcConfigDao dao = new JdbcConfigDao(jdbc, enc);
        configService = new ConfigurationService(dao);
    }

    @Test
    void snapshotAndHashChangeDetection() {
        String json1 = ConfigExportUtil.buildFullSnapshotJson(configService);
        String hash1 = ConfigExportUtil.sha256(json1);
        // mutate a value
        configService.set(CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, 42);
        String json2 = ConfigExportUtil.buildFullSnapshotJson(configService);
        String hash2 = ConfigExportUtil.sha256(json2);
        assertNotEquals(hash1, hash2);
        // same again should be stable
        String json3 = ConfigExportUtil.buildFullSnapshotJson(configService);
        String hash3 = ConfigExportUtil.sha256(json3);
        assertEquals(hash2, hash3);
    }

    @Test
    void parseFlatJsonRoundTrip() {
        String json = ConfigExportUtil.buildFullSnapshotJson(configService);
        var parsed = ConfigExportUtil.parseFlatJson(json);
        assertTrue(parsed.containsKey(CoreConfigKeys.EVENT_BUS_MAX_EVENTS.getName()));
    }
}
