package com.voxstream.core.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.voxstream.core.config.VoxStreamConfiguration;
import com.voxstream.core.event.BaseEvent;
import com.voxstream.core.event.EventMetadata;
import com.voxstream.core.event.EventType;
import com.voxstream.core.persistence.EventPersistenceService;
import com.voxstream.core.service.CoreErrorHandler;

/**
 * Tests for cleanup/purging logic (expiration + size enforcement).
 */
public class EventBusCleanupTest {

    private AnnotationConfigApplicationContext baseContext(String dbName) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(VoxStreamConfiguration.class);
        ctx.registerBean(CoreErrorHandler.class);
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS events (id VARCHAR(36) PRIMARY KEY, type VARCHAR(64), source_platform VARCHAR(32), created_at TIMESTAMP, expires_at TIMESTAMP NULL, importance INT, correlation_id VARCHAR(64), payload CLOB NULL)");
            return jdbc;
        });
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        ctx.registerBean(EventPersistenceService.class);
        ctx.registerBean(EventBusImpl.class);
        ctx.refresh();
        return ctx;
    }

    private AnnotationConfigApplicationContext smallBufferContext(String dbName, int buffer) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(VoxStreamConfiguration.class, () -> {
            VoxStreamConfiguration cfg = new VoxStreamConfiguration();
            cfg.getEventBus().setMaxEvents(buffer);
            cfg.getEventBus().setBufferSize(buffer);
            return cfg;
        });
        ctx.registerBean(CoreErrorHandler.class);
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS events (id VARCHAR(36) PRIMARY KEY, type VARCHAR(64), source_platform VARCHAR(32), created_at TIMESTAMP, expires_at TIMESTAMP NULL, importance INT, correlation_id VARCHAR(64), payload CLOB NULL)");
            return jdbc;
        });
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        ctx.registerBean(EventPersistenceService.class);
        ctx.registerBean(EventBusImpl.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void expiredEventsArePurged() {
        try (AnnotationConfigApplicationContext ctx = baseContext("purge-expired")) {
            EventBusImpl bus = ctx.getBean(EventBusImpl.class);

            // Create 3 events: 2 expired, 1 valid
            Instant past = Instant.now().minusSeconds(60);
            Instant future = Instant.now().plusSeconds(60);
            bus.publish(new BaseEvent(EventType.SYSTEM, "expired1", Map.of(),
                    EventMetadata.builder().expiresAt(past).build()));
            bus.publish(new BaseEvent(EventType.SYSTEM, "expired2", Map.of(),
                    EventMetadata.builder().expiresAt(past).build()));
            bus.publish(new BaseEvent(EventType.SYSTEM, "alive", Map.of(),
                    EventMetadata.builder().expiresAt(future).build()));

            // allow async dispatch persistence
            sleep(200);
            bus.maintenanceTick();

            List<?> remaining = bus.recentEvents(10);
            assertEquals(1, remaining.size(), "Only non-expired event should remain");
        }
    }

    @Test
    void sizeEnforcementDropsOldestWhenExceedingBuffer() {
        try (AnnotationConfigApplicationContext ctx = smallBufferContext("purge-size", 25)) {
            EventBusImpl bus = ctx.getBean(EventBusImpl.class);
            int publish = 100; // 4x capacity
            for (int i = 0; i < publish; i++) {
                bus.publish(
                        new BaseEvent(EventType.CHAT_MESSAGE, "msg" + i, Map.of(), EventMetadata.builder().build()));
            }
            sleep(300);
            bus.maintenanceTick();
            List<?> events = bus.recentEvents(200);
            assertTrue(events.size() <= 25,
                    "Buffer should have been trimmed to capacity (<=25) but was " + events.size());
        }
    }

    @Test
    void expiredAndOverCapacityCombined() {
        try (AnnotationConfigApplicationContext ctx = smallBufferContext("purge-combined", 30)) {
            EventBusImpl bus = ctx.getBean(EventBusImpl.class);
            Instant past = Instant.now().minusSeconds(30);
            for (int i = 0; i < 90; i++) { // 3x capacity
                EventMetadata.Builder mb = EventMetadata.builder();
                if (i % 4 == 0) {
                    mb.expiresAt(past);
                }
                bus.publish(new BaseEvent(EventType.SYSTEM, "e" + i, Map.of("i", i), mb.build()));
            }
            sleep(400);
            bus.maintenanceTick();
            List<?> remaining = bus.recentEvents(200);
            assertTrue(remaining.size() <= 30, "Should enforce max events");
        }
    }

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
