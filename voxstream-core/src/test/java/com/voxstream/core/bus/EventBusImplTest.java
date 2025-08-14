package com.voxstream.core.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
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

public class EventBusImplTest {

    private void registerInfrastructure(AnnotationConfigApplicationContext ctx) {
        // DataSource
        ctx.registerBean(JdbcTemplate.class, () -> {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:voxstream-test;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            // Run migration minimal (inline)
            jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS events (id VARCHAR(36) PRIMARY KEY, type VARCHAR(64), source_platform VARCHAR(32), created_at TIMESTAMP, expires_at TIMESTAMP NULL, importance INT, correlation_id VARCHAR(64), payload CLOB NULL)");
            return jdbc;
        });
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void publishDeliverSimple() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(VoxStreamConfiguration.class);
            ctx.registerBean(CoreErrorHandler.class);
            registerInfrastructure(ctx);
            ctx.registerBean(EventPersistenceService.class);
            ctx.registerBean(EventBusImpl.class);
            ctx.refresh();

            EventBus bus = ctx.getBean(EventBus.class);

            CountDownLatch latch = new CountDownLatch(1);
            bus.subscribe(new EventSubscription(e -> e.getType() == EventType.SYSTEM, ev -> latch.countDown(), 0));

            bus.publish(new BaseEvent(EventType.SYSTEM, "test", Map.of("k", "v"), EventMetadata.builder().build()));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Event was not delivered in time");
        }
    }

    @Test
    void filteringWorks() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(VoxStreamConfiguration.class);
            ctx.registerBean(CoreErrorHandler.class);
            registerInfrastructure(ctx);
            ctx.registerBean(EventPersistenceService.class);
            ctx.registerBean(EventBusImpl.class);
            ctx.refresh();

            EventBus bus = ctx.getBean(EventBus.class);

            CountDownLatch latch = new CountDownLatch(1);
            bus.subscribe(
                    new EventSubscription(e -> e.getType() == EventType.CHAT_MESSAGE, ev -> latch.countDown(), 0));

            bus.publish(new BaseEvent(EventType.SYSTEM, "test", Map.of(), EventMetadata.builder().build()));
            bus.publish(new BaseEvent(EventType.CHAT_MESSAGE, "test", Map.of(), EventMetadata.builder().build()));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Filtered event not delivered");
        }
    }

    @Test
    void priorityOrdering() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(VoxStreamConfiguration.class);
            ctx.registerBean(CoreErrorHandler.class);
            registerInfrastructure(ctx);
            ctx.registerBean(EventPersistenceService.class);
            ctx.registerBean(EventBusImpl.class);
            ctx.refresh();

            EventBusImpl bus = ctx.getBean(EventBusImpl.class);

            StringBuilder order = new StringBuilder();
            bus.subscribe(new EventSubscription(e -> true, e -> order.append("A"), 1));
            bus.subscribe(new EventSubscription(e -> true, e -> order.append("B"), 5));
            bus.publish(new BaseEvent(EventType.SYSTEM, "test", Map.of(), EventMetadata.builder().build()));

            Thread.sleep(500); // allow dispatch
            // B should precede A due to higher priority 5 > 1
            assertEquals("BA", order.toString());
        }
    }
}
