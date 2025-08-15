package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies SPI fallback discovery loads dummy & dummy2 factories when no Spring
 * context is present.
 */
public class PlatformConnectionRegistrySpiDiscoveryTest {

    @Test
    void spiLoadsDummyFactories() {
        // Construct registry with empty Spring-discovered collection to force SPI path
        PlatformConnectionRegistry reg = new PlatformConnectionRegistry(java.util.List.of());
        Set<String> ids = Set.copyOf(reg.platformIds());
        assertTrue(ids.contains("dummy"), "dummy platform expected via SPI");
        assertTrue(ids.contains("dummy2"), "dummy2 platform expected via SPI");
        // Ensure metadata present
        assertEquals("Dummy Platform", reg.metadata("dummy").get().displayName());
        assertEquals("Dummy Second Platform", reg.metadata("dummy2").get().displayName());
    }
}
