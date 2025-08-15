package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.voxstream.core.bus.EventBus;
import com.voxstream.core.bus.EventBusImpl;
import com.voxstream.core.bus.EventSubscription;
import com.voxstream.core.config.ConfigurationService;
import com.voxstream.core.config.VoxStreamConfiguration;
import com.voxstream.core.config.keys.CoreConfigKeys;
import com.voxstream.core.dao.ConfigDao;
import com.voxstream.core.dao.jdbc.JdbcConfigDao;
import com.voxstream.core.event.Event;
import com.voxstream.core.event.EventType;
import com.voxstream.core.persistence.EventPersistenceService;
import com.voxstream.core.security.EncryptionService;
import com.voxstream.core.service.CoreErrorHandler;
import com.voxstream.platform.api.PlatformStatus;
import com.voxstream.platform.api.dummy.DummyPlatformConnection;
import com.voxstream.platform.api.dummy.DummyPlatformConnectionFactory;

/**
 * Verifies that PlatformConnectionManager publishes ordered status lifecycle
 * events
 * onto the EventBus as SYSTEM events.
 */
public class PlatformConnectionManagerStatusEventTest {

    private AnnotationConfigApplicationContext ctx() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CoreErrorHandler.class);
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        ctx.registerBean(VoxStreamConfiguration.class);
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:plat-mgr-status-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
        ctx.registerBean(EventBusImpl.class);
        ctx.registerBean(DummyPlatformConnectionFactory.class);
        ctx.registerBean(PlatformConnectionRegistry.class);
        ctx.registerBean(PlatformConnectionManager.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void statusEventPropagationSequence() throws Exception {
        try (AnnotationConfigApplicationContext c = ctx()) {
            ConfigurationService cfg = c.getBean(ConfigurationService.class);
            cfg.set(CoreConfigKeys.PLATFORM_ENABLED, true);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, 100);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, 800);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, 5);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_JITTER_PERCENT, 0.0d); // deterministic
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_RESET_AFTER_STABLE_MS, 10_000);

            DummyPlatformConnection dummy = (DummyPlatformConnection) c.getBean(PlatformConnectionRegistry.class)
                    .get(DummyPlatformConnection.PLATFORM_ID).orElseThrow();
            dummy.setSimulatedLatencyMs(25);
            dummy.setScriptedOutcomes(List.of(false, true)); // initial failure then success

            EventBus bus = c.getBean(EventBus.class);
            List<PlatformStatus.State> observedStates = new ArrayList<>();
            CountDownLatch connectedLatch = new CountDownLatch(1);
            bus.subscribe(new EventSubscription(e -> e.getType() == EventType.SYSTEM &&
                    DummyPlatformConnection.PLATFORM_ID.equals(e.getSourcePlatform()),
                    ev -> captureState(observedStates, connectedLatch).accept(ev), 0));

            PlatformConnectionManager mgr = c.getBean(PlatformConnectionManager.class);
            mgr.start();

            assertTrue(connectedLatch.await(6, TimeUnit.SECONDS), "Did not observe CONNECTED state in time");
            // Give a short buffer for any trailing events to arrive
            TimeUnit.MILLISECONDS.sleep(150);

            // Expected ordered subsequence: CONNECTING, FAILED, RECONNECT_SCHEDULED,
            // CONNECTING, CONNECTED
            List<PlatformStatus.State> expected = List.of(
                    PlatformStatus.State.CONNECTING,
                    PlatformStatus.State.FAILED,
                    PlatformStatus.State.RECONNECT_SCHEDULED,
                    PlatformStatus.State.CONNECTING,
                    PlatformStatus.State.CONNECTED);

            int searchFrom = 0;
            for (PlatformStatus.State exp : expected) {
                boolean found = false;
                for (int i = searchFrom; i < observedStates.size(); i++) {
                    if (observedStates.get(i) == exp) {
                        found = true;
                        searchFrom = i + 1; // next search begins after this index
                        break;
                    }
                }
                assertTrue(found, "Did not find expected state " + exp + " in order. Observed=" + observedStates);
            }

            // Final state should be CONNECTED
            assertEquals(PlatformStatus.State.CONNECTED, observedStates.get(observedStates.size() - 1));
        }
    }

    private java.util.function.Consumer<Event> captureState(List<PlatformStatus.State> list, CountDownLatch latch) {
        return ev -> {
            Object stateVal = ev.getPayload().get("state");
            if (stateVal instanceof String s) {
                try {
                    PlatformStatus.State st = PlatformStatus.State.valueOf(s);
                    list.add(st);
                    if (st == PlatformStatus.State.CONNECTED) {
                        latch.countDown();
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        };
    }
}
