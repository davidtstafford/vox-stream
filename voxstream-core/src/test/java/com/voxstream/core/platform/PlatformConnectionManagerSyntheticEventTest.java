package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
import com.voxstream.platform.api.dummy.DummyPlatformConnection;
import com.voxstream.platform.api.dummy.DummyPlatformConnectionFactory;

/**
 * Verifies synthetic platform events emitted by DummyPlatformConnection are
 * published
 * onto the EventBus as UNKNOWN events with expected payload shape via
 * PlatformConnectionManager.
 */
public class PlatformConnectionManagerSyntheticEventTest {

    private AnnotationConfigApplicationContext ctx() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CoreErrorHandler.class);
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        ctx.registerBean(VoxStreamConfiguration.class);
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:plat-mgr-synth-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
    void syntheticEventsPublishedToBus() throws Exception {
        try (AnnotationConfigApplicationContext c = ctx()) {
            ConfigurationService cfg = c.getBean(ConfigurationService.class);
            cfg.set(CoreConfigKeys.PLATFORM_ENABLED, true);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_INITIAL_DELAY_MS, 100);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_DELAY_MS, 800);
            cfg.set(CoreConfigKeys.PLATFORM_RECONNECT_MAX_ATTEMPTS, 1);

            DummyPlatformConnection dummy = (DummyPlatformConnection) c.getBean(PlatformConnectionRegistry.class)
                    .get(DummyPlatformConnection.PLATFORM_ID).orElseThrow();
            dummy.setSimulatedLatencyMs(15);
            dummy.setScriptedOutcomes(List.of(true)); // immediate success
            dummy.setEmitSyntheticEvents(true);

            EventBus bus = c.getBean(EventBus.class);
            List<Event> received = new CopyOnWriteArrayList<>();
            bus.subscribe(new EventSubscription(e -> e.getType() == EventType.UNKNOWN
                    && DummyPlatformConnection.PLATFORM_ID.equals(e.getSourcePlatform()), received::add, 0));

            PlatformConnectionManager mgr = c.getBean(PlatformConnectionManager.class);
            mgr.start();

            // Wait for initial connect + first synthetic event (emitted during connect)
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && received.isEmpty()) {
                TimeUnit.MILLISECONDS.sleep(25);
            }
            assertFalse(received.isEmpty(), "Expected at least one synthetic UNKNOWN event after connect");

            // Emit additional synthetic events
            for (int i = 0; i < 3; i++) {
                dummy.tickSyntheticEvent();
            }
            TimeUnit.MILLISECONDS.sleep(150); // allow dispatch

            assertTrue(received.size() >= 2, "Expected multiple synthetic events");
            // Validate payload shape
            Event first = received.get(0);
            assertNotNull(first.getPayload(), "Payload should not be null");
            assertTrue(first.getPayload().containsKey("platformType"));
            assertTrue(first.getPayload().containsKey("rawTimestamp"));
            Object ts = first.getPayload().get("rawTimestamp");
            assertTrue(ts instanceof Number && ((Number) ts).longValue() > 0L, "rawTimestamp should be positive long");
        }
    }
}
