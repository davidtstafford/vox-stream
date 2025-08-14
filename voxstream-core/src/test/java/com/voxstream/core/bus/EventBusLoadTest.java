package com.voxstream.core.bus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
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
 * Lightweight load/latency smoke test for EventBus (not a performance
 * benchmark).
 */
public class EventBusLoadTest {

    @Test
    void publishManyMeasureLatency() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(VoxStreamConfiguration.class);
            ctx.registerBean(CoreErrorHandler.class);
            ctx.registerBean(JdbcTemplate.class, () -> {
                JdbcDataSource ds = new JdbcDataSource();
                ds.setURL("jdbc:h2:mem:voxstream-load;DB_CLOSE_DELAY=-1");
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

            EventBus bus = ctx.getBean(EventBus.class);

            final int total = 2000; // modest count for CI runtime
            CountDownLatch latch = new CountDownLatch(total);
            long start = System.nanoTime();
            bus.subscribe(new EventSubscription(e -> true, e -> latch.countDown(), 0));

            for (int i = 0; i < total; i++) {
                EventType type = (i % 5 == 0) ? EventType.SYSTEM : EventType.CHAT_MESSAGE;
                bus.publish(new BaseEvent(type, "payload" + i, Map.of("i", i),
                        EventMetadata.builder().importance(ThreadLocalRandom.current().nextInt(0, 5)).build()));
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Events not drained in time");
            long elapsedNs = System.nanoTime() - start;
            double avgMicros = (elapsedNs / 1000.0) / total;

            // Soft assertion: average dispatch under 5,000 microseconds (5 ms) per event in
            // this synthetic test.
            assertTrue(avgMicros < 5000, "Avg dispatch too high: " + avgMicros + "µs");
        }
    }
}
