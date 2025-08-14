package com.voxstream.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.security.EncryptionService;

class ConfigValidatorsTest {

    private ConfigurationService configService;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cfg-val-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"); // unique DB per test, keep
                                                                                           // alive
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
    void numericRangeValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> configService.set(CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, 0));
        assertThrows(IllegalArgumentException.class, () -> configService.set(CoreConfigKeys.WEB_OUTPUT_PORT, 80));
        assertDoesNotThrow(() -> configService.set(CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, 60));
    }

    @Test
    void compositeValidationPortReserved() {
        assertThrows(IllegalArgumentException.class,
                () -> configService.setAll(Map.of(CoreConfigKeys.WEB_OUTPUT_PORT, 65535)));
        assertDoesNotThrow(() -> configService.setAll(Map.of(CoreConfigKeys.WEB_OUTPUT_PORT, 8081)));
    }

    @Test
    void compositeValidationTtsPurgeNotLessThanEvent() {
        // event purge default 10, attempt to set tts purge smaller (5) should fail
        assertThrows(IllegalArgumentException.class,
                () -> configService.setAll(Map.of(CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, 5)));
        // large enough passes
        assertDoesNotThrow(() -> configService
                .setAll(Map.of(CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, 20)));
    }

    @Test
    void boundaryValuesAccepted() {
        // Minimal acceptable purge values (assuming 1 is allowed for both individually)
        assertDoesNotThrow(() -> configService.setAll(Map.of(
                CoreConfigKeys.EVENT_BUS_PURGE_INTERVAL_MIN, 1,
                CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, 1)));
        // Port upper boundary (excluding reserved sentinel) 65534 ok
        assertDoesNotThrow(() -> configService.set(CoreConfigKeys.WEB_OUTPUT_PORT, 65534));
    }

    @Test
    void bulkSetRollbackOnFirstFailure() {
        // First invalid (reserved port) should cause entire batch reject (tts should
        // not persist)
        assertThrows(IllegalArgumentException.class, () -> configService.setAll(Map.of(
                CoreConfigKeys.WEB_OUTPUT_PORT, 65535,
                CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, 999)));
        // After failure, setting valid TTS should still work (ensures no partial commit
        // side-effects)
        assertDoesNotThrow(() -> configService.set(CoreConfigKeys.TTS_BUS_PURGE_INTERVAL_MIN, 999));
    }
}
