package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.voxstream.platform.api.PlatformStatus;
import com.voxstream.platform.api.dummy.DummyPlatformConnection;
import com.voxstream.platform.api.dummy.DummyPlatformConnectionFactory;

/**
 * Tests backoff growth and reset logic including jitter margin and fatal stop.
 */
public class PlatformConnectionManagerBackoffTest {

    private AnnotationConfigApplicationContext ctx() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CoreErrorHandler.class);
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        ctx.registerBean(VoxStreamConfiguration.class);
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:plat-mgr-backoff-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            JdbcTemplate jt = new JdbcTemplate(ds);
            jt.execute(
                    "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            jt.execute(
                    "CREATE TABLE IF NOT EXISTS events (id VARCHAR(36) PRIMARY KEY, type VARCHAR(64), source_platform VARCHAR(32), created_at TIMESTAMP, expires_at TIMESTAMP NULL, importance INT, correlation_id VARCHAR(64), payload CLOB NULL)");
            return jt;
        });
        ctx.registerBean(EncryptionService.class);
        ctx.registerBean(ConfigDao.class,
                () -> new JdbcConfigDao(ctx.getBean(JdbcTemplate.class), ctx.getBean(EncryptionService.class)));
        ctx.registerBean(ConfigurationService.class);
        ctx.registerBean(EventPersistenceService.class); // add missing persistence bean
        ctx.registerBean(EventBusImpl.class);
        ctx.registerBean(DummyPlatformConnectionFactory.class);
        ctx.registerBean(PlatformConnectionRegistry.class);
        ctx.registerBean(PlatformConnectionManager.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void backoffGrowthAndReset() throws Exception {
        try (AnnotationConfigApplicationContext c = ctx()) {
            ConfigurationService cfg = c.getBean(ConfigurationService.class);
            cfg.set(CoreConfigKeys.PLATFORM_ENABLED, true);
            cfg.setDynamicBoolean("platform.dummy.enabled", true);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, 100);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, 2000);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, -1);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_JITTER_PERCENT, 0.0d); // disable jitter for deterministic growth
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_RESET_AFTER_STABLE_MS, 10_000); // minimum allowed

            DummyPlatformConnection dummy = (DummyPlatformConnection) c.getBean(PlatformConnectionRegistry.class)
                    .get("dummy").orElseThrow();
            dummy.setSimulatedLatencyMs(30);
            // 3 transient failures then success -> backoff sequence: attempt0 (init=100),
            // then schedule 200, 400, 800 then connect success
            dummy.setScriptedOutcomes(List.of(false, false, false, true));

            PlatformConnectionManager mgr = c.getBean(PlatformConnectionManager.class);
            mgr.start();

            // wait until connected
            long deadline = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < deadline
                    && mgr.status("dummy").state() != PlatformStatus.State.CONNECTED) {
                TimeUnit.MILLISECONDS.sleep(25);
            }
            assertEquals(PlatformStatus.State.CONNECTED, mgr.status("dummy").state(),
                    "Should connect after scripted failures");
            PlatformConnectionManager.Metrics m = mgr.metrics("dummy");
            assertEquals(800, m.currentBackoffMs, "Backoff should be last exponential delay before success");

            // Now wait for reset (10s) with safety timeout 15s
            long resetDeadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < resetDeadline && m.currentBackoffMs != 0) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
            assertEquals(0, m.currentBackoffMs, "Backoff should reset to 0 after 10s stable connection period");
        }
    }

    @Test
    void jitterAppliedWithinRange() throws Exception {
        try (AnnotationConfigApplicationContext c = ctx()) {
            ConfigurationService cfg = c.getBean(ConfigurationService.class);
            cfg.set(CoreConfigKeys.PLATFORM_ENABLED, true);
            cfg.setDynamicBoolean("platform.dummy.enabled", true);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, 100);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, 2000);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, 2); // limit attempts
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_JITTER_PERCENT, 0.3d); // 30% jitter
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_RESET_AFTER_STABLE_MS, 10_000); // valid, won't elapse in test

            DummyPlatformConnection dummy = (DummyPlatformConnection) c.getBean(PlatformConnectionRegistry.class)
                    .get("dummy").orElseThrow();
            dummy.setSimulatedLatencyMs(10);
            dummy.setScriptedOutcomes(List.of(false, false, true)); // two failures then success

            PlatformConnectionManager mgr = c.getBean(PlatformConnectionManager.class);
            mgr.start();

            // wait for final state
            long deadline = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < deadline
                    && mgr.status("dummy").state() != PlatformStatus.State.CONNECTED) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            assertEquals(PlatformStatus.State.CONNECTED, mgr.status("dummy").state());
            PlatformConnectionManager.Metrics m = mgr.metrics("dummy");
            int observed = m.currentBackoffMs; // last scheduled delay prior to success (base 400 +/- jitter)
            int base = 400;
            int range = (int) Math.round(base * 0.3d);
            assertTrue(observed >= base - range && observed <= base + range, "Observed backoff within jitter range");
        }
    }

    @Test
    void fatalStopsReconnection() throws Exception {
        try (AnnotationConfigApplicationContext c = ctx()) {
            ConfigurationService cfg = c.getBean(ConfigurationService.class);
            cfg.set(CoreConfigKeys.PLATFORM_ENABLED, true);
            cfg.setDynamicBoolean("platform.dummy.enabled", true);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, 100);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, 2000);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, -1);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_JITTER_PERCENT, 0.0d);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_RESET_AFTER_STABLE_MS, 10_000);

            DummyPlatformConnection dummy = (DummyPlatformConnection) c.getBean(PlatformConnectionRegistry.class)
                    .get("dummy").orElseThrow();
            dummy.setSimulatedLatencyMs(20);
            dummy.setScriptedOutcomes(List.of(false, "F")); // transient failure then fatal

            PlatformConnectionManager mgr = c.getBean(PlatformConnectionManager.class);
            mgr.start();

            long wait = System.currentTimeMillis() + 6000;
            boolean fatalSeen = false;
            while (System.currentTimeMillis() < wait) {
                PlatformStatus st = mgr.status("dummy");
                if (st.state() == PlatformStatus.State.FAILED && st.fatal()) {
                    fatalSeen = true;
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(25);
            }
            assertTrue(fatalSeen, "Fatal failure status should be observed");
            // Allow scheduling window to ensure no further attempts
            TimeUnit.MILLISECONDS.sleep(500);
            PlatformConnectionManager.Metrics m = mgr.metrics("dummy");
            assertEquals(2, m.failedAttempts, "No further reconnect attempts after fatal");
        }
    }
}
