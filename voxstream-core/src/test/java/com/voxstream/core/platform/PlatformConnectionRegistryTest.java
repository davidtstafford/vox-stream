package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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
            "com.voxstream.platform.api.dummy" })
    static class TestConfig {
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
