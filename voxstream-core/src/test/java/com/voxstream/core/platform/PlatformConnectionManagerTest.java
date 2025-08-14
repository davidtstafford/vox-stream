package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.voxstream.core.bus.EventBusImpl;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.VoxStreamConfiguration;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.ConfigDao;
import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.persistence.EventPersistenceService;
import com.voxstream.core.security.EncryptionService;
import com.voxstream.core.service.CoreErrorHandler;
import com.voxstream.platform.api.dummy.DummyPlatformConnection;
import com.voxstream.platform.api.dummy.DummyPlatformConnectionFactory;

/**
 * Tests for PlatformConnectionManager basic lifecycle and auto-reconnect using
 * DummyPlatformConnection failure script.
 */
public class PlatformConnectionManagerTest {

    private AnnotationConfigApplicationContext baseCtx() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CoreErrorHandler.class);
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        ctx.registerBean(VoxStreamConfiguration.class); // Added missing config bean
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:plat-mgr-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            JdbcTemplate jt = new JdbcTemplate(ds);
            jt.execute(
                    "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            jt.execute(
                    "CREATE TABLE IF NOT EXISTS events (id VARCHAR(36) PRIMARY KEY, type VARCHAR(64), source_platform VARCHAR(32), created_at TIMESTAMP, expires_at TIMESTAMP NULL, importance INT, correlation_id VARCHAR(64), payload CLOB NULL)");
            return jt;
        });
        // Added encryption service bean required by JdbcConfigDao constructor
        ctx.registerBean(EncryptionService.class);
        ctx.registerBean(ConfigDao.class,
                () -> new JdbcConfigDao(ctx.getBean(JdbcTemplate.class), ctx.getBean(EncryptionService.class)));
        ctx.registerBean(ConfigurationService.class);
        ctx.registerBean(EventPersistenceService.class);
        ctx.registerBean(EventBusImpl.class);
        ctx.registerBean(DummyPlatformConnectionFactory.class);
        ctx.registerBean(PlatformConnectionRegistry.class);
        ctx.registerBean(PlatformConnectionManager.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void lifecycleAndReconnect() throws Exception {
        try (AnnotationConfigApplicationContext ctx = baseCtx()) {
            ConfigurationService cfg = ctx.getBean(ConfigurationService.class);
            cfg.set(CoreConfigKeys.PLATFORM_ENABLED, true);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, 100);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, 800);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, 5);

            // Prepare dummy connection scripted failures BEFORE manager start so initial
            // attempt fails
            DummyPlatformConnection dummy = (DummyPlatformConnection) ctx.getBean(PlatformConnectionRegistry.class)
                    .get("dummy").orElseThrow();
            dummy.setSimulatedLatencyMs(25);
            dummy.setScriptedOutcomes(List.of(false, false, true)); // two failures then success

            PlatformConnectionManager mgr = ctx.getBean(PlatformConnectionManager.class);
            mgr.start();

            long deadline = System.currentTimeMillis() + 4000;
            boolean connected = false;
            while (System.currentTimeMillis() < deadline) {
                if (mgr.status("dummy").state() == com.voxstream.platform.api.PlatformStatus.State.CONNECTED) {
                    connected = true;
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(50);
            }
            assertTrue(connected, "Dummy should eventually connect after scripted failures");
            var metrics = mgr.metrics("dummy");
            assertTrue(metrics.failedAttempts >= 2, "Expected at least 2 failed attempts");
        }
    }
}
