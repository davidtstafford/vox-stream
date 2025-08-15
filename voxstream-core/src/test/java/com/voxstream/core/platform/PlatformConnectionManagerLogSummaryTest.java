package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Verifies periodic status summary using internal test interval override +
 * hook.
 */
public class PlatformConnectionManagerLogSummaryTest {

    private AnnotationConfigApplicationContext ctx;
    private final List<List<PlatformConnectionManager.SummaryRow>> captured = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setup() {
        System.setProperty(PlatformConnectionManager.TEST_LOG_SUMMARY_INTERVAL_MS_PROP, "80"); // 80ms interval
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CoreErrorHandler.class);
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        ctx.registerBean(VoxStreamConfiguration.class);
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:plat-mgr-log-summary-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
        ctx.registerBean(EventPersistenceService.class);
        ctx.registerBean(EventBusImpl.class); // added event bus bean
        ctx.registerBean(DummyPlatformConnectionFactory.class);
        ctx.registerBean(PlatformConnectionRegistry.class);
        ctx.registerBean(PlatformConnectionManager.class);
        ctx.refresh();
    }

    @AfterEach
    void cleanup() {
        System.clearProperty(PlatformConnectionManager.TEST_LOG_SUMMARY_INTERVAL_MS_PROP);
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void periodicSummaryHookReceivesRows() throws Exception {
        ConfigurationService cfg = ctx.getBean(ConfigurationService.class);
        cfg.set(CoreConfigKeys.PLATFORM_ENABLED, true);
        cfg.setDynamicBoolean("platform.dummy.enabled", true);
        cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, 100);
        cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, 500);
        cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, 2);
        cfg.set(CoreConfigKeys.PLATFORM_STATUS_LOG_SUMMARY_ENABLED, true);
        cfg.set(CoreConfigKeys.PLATFORM_STATUS_LOG_SUMMARY_INTERVAL_SEC, 60); // validator min satisfied

        DummyPlatformConnection dummy = (DummyPlatformConnection) ctx.getBean(PlatformConnectionRegistry.class)
                .get(DummyPlatformConnection.PLATFORM_ID).orElseThrow();
        dummy.setSimulatedLatencyMs(5);
        dummy.setScriptedOutcomes(List.of(true));
        dummy.setEmitSyntheticEvents(true);

        PlatformConnectionManager mgr = ctx.getBean(PlatformConnectionManager.class);
        mgr.addTestSummaryHook(rows -> captured.add(List.copyOf(rows)));
        mgr.start();

        // tick synthetic events a few times
        for (int i = 0; i < 5; i++) {
            dummy.tickSyntheticEvent();
        }

        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline && captured.size() < 2) {
            TimeUnit.MILLISECONDS.sleep(40);
        }
        assertTrue(captured.size() >= 2, "Expected at least 2 captured summary snapshots");
        boolean hasDummy = captured.stream()
                .anyMatch(list -> list.stream().anyMatch(r -> r.platformId().equals("dummy")));
        assertTrue(hasDummy, "At least one summary row for dummy platform expected");
        // Assert synthetic events were emitted
        assertTrue(dummy.syntheticEventCount() >= 1, "Expected synthetic events to be emitted");
    }
}
