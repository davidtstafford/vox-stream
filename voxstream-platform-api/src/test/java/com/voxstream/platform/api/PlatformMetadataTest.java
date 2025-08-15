package com.voxstream.platform.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PlatformMetadataTest {

    @Test
    void testImmutableCapabilities() {
        PlatformMetadata md = new PlatformMetadata("dummy", "Dummy", "1.0.0",
                Set.of(Capability.EVENTS, Capability.CHAT));
        assertEquals("dummy", md.id());
        assertEquals("Dummy", md.displayName());
        assertTrue(md.capabilities().contains(Capability.EVENTS));
        assertThrows(UnsupportedOperationException.class, () -> md.capabilities().add(Capability.TTS_INPUT));
    }
}
