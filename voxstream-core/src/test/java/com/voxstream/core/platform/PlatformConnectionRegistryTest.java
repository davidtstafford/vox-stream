package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformStatus.State;

/**
 * Integration-style test verifying Spring discovery + dummy connection
 * lifecycle.
 */
public class PlatformConnectionRegistryTest {

    @Configuration
    @ComponentScan(basePackages = {
            "com.voxstream.core.platform",
            "com.voxstream.platform.api.dummy",
            "com.voxstream.core.config",
            "com.voxstream.core.security",
            "com.voxstream.core.dao.jdbc",
            "com.voxstream.core.bus",
            // Added scans for required dependencies
            "com.voxstream.core.service",
            "com.voxstream.core.persistence" })
    static class TestConfig {
        @Bean
        JdbcTemplate jdbcTemplate() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:plat-reg-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            JdbcTemplate jt = new JdbcTemplate(ds);
            jt.execute(
                    "CREATE TABLE IF NOT EXISTS app_config (cfg_key VARCHAR(128) PRIMARY KEY, cfg_value CLOB NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            // Needed for EventBusImpl persistence dependency when enabled
            jt.execute(
                    "CREATE TABLE IF NOT EXISTS events (id VARCHAR(36) PRIMARY KEY, type VARCHAR(64), source_platform VARCHAR(32), created_at TIMESTAMP, expires_at TIMESTAMP NULL, importance INT, correlation_id VARCHAR(64), payload CLOB NULL)");
            return jt;
        }
    }

    @BeforeEach
    void ensurePlatformEnabledConfig() {
        // will rely on default of platform.enabled=false, manager will skip starting
        // connection; we manually connect below
    }

    @Test
    void registryDiscoversDummyAndConnects() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            PlatformConnectionRegistry registry = ctx.getBean(PlatformConnectionRegistry.class);
            // ensure factory present
            assertTrue(registry.platformIds().contains("dummy"));
            assertEquals(List.of("dummy"), registry.platformIds().stream().sorted().toList());

            PlatformConnection conn = registry.get("dummy").orElseThrow();
            assertEquals(State.DISCONNECTED, conn.status().state());

            boolean result = conn.connect().get(1, TimeUnit.SECONDS);
            assertTrue(result);
            assertEquals(State.CONNECTED, conn.status().state());

            conn.disconnect().get(1, TimeUnit.SECONDS);
            assertEquals(State.DISCONNECTED, conn.status().state());
        }
    }
}
