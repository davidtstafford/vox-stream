package com.voxstream.core.config.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.security.EncryptionService;

class ProfileServiceTest {

    private ConfigurationService configService;
    private ProfileService profileService;
    private String dbName;

    @BeforeEach
    void setup() {
        dbName = "profile-test-" + System.nanoTime();
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        EncryptionService enc = new EncryptionService();
        JdbcConfigDao dao = new JdbcConfigDao(jdbc, enc);
        configService = new ConfigurationService(dao);
        profileService = new ProfileService(dao, configService);
    }

    @Test
    void profileRoundTrip() {
        configService.set(CoreConfigKeys.EVENT_BUS_MAX_EVENTS, 5000);
        configService.set(CoreConfigKeys.WEB_OUTPUT_ENABLE_CORS, false);
        profileService.saveProfile("testA");
        // mutate
        configService.set(CoreConfigKeys.EVENT_BUS_MAX_EVENTS, 1234);
        configService.set(CoreConfigKeys.WEB_OUTPUT_ENABLE_CORS, true);
        profileService.applyProfile("testA");
        assertEquals(5000, configService.get(CoreConfigKeys.EVENT_BUS_MAX_EVENTS));
        assertFalse(configService.get(CoreConfigKeys.WEB_OUTPUT_ENABLE_CORS));
    }

    @Test
    void listAndDelete() {
        profileService.saveProfile("p1");
        profileService.saveProfile("p2");
        List<String> names = profileService.listProfiles();
        assertTrue(names.contains("p1"));
        assertTrue(names.contains("p2"));
        profileService.deleteProfile("p1");
        assertFalse(profileService.listProfiles().contains("p1"));
    }

    @Test
    void defaultProfileAutoApply() {
        configService.set(CoreConfigKeys.EVENT_BUS_MAX_EVENTS, 7777);
        profileService.saveProfile("auto");
        profileService.setDefaultProfile("auto");
        // change value then create a new service stack -> default should apply
        configService.set(CoreConfigKeys.EVENT_BUS_MAX_EVENTS, 1111);
        // rebuild services using same underlying table
        JdbcDataSource ds2 = new JdbcDataSource();
        ds2.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        ds2.setUser("sa");
        ds2.setPassword("");
        JdbcTemplate jdbc2 = new JdbcTemplate(ds2);
        jdbc2.execute(
                "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        EncryptionService enc2 = new EncryptionService();
        JdbcConfigDao dao2 = new JdbcConfigDao(jdbc2, enc2);
        ConfigurationService cs2 = new ConfigurationService(dao2);
        // instantiate new profile service (applies default in ctor)
        new ProfileService(dao2, cs2);
        assertEquals(7777, cs2.get(CoreConfigKeys.EVENT_BUS_MAX_EVENTS));
    }

    @Test
    void invalidNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> profileService.saveProfile("invalid name with space"));
        assertThrows(IllegalArgumentException.class, () -> profileService.saveProfile("$bad"));
    }

    @Test
    void checksumStable() {
        profileService.saveProfile("c1");
        String a = profileService.checksumProfile("c1");
        String b = profileService.checksumProfile("c1");
        assertEquals(a, b);
    }
}
