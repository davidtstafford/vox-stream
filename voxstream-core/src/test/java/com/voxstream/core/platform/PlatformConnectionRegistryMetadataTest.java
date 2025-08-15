package com.voxstream.core.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.voxstream.platform.api.Capability;
import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;
import com.voxstream.platform.api.PlatformMetadata;
import com.voxstream.platform.api.PlatformStatus;

/** Tests metadata exposure via registry using inline factories */
class PlatformConnectionRegistryMetadataTest {

    static class TestConn implements PlatformConnection {
        private final String id;

        TestConn(String id) {
            this.id = id;
        }

        @Override
        public CompletableFuture<Boolean> connect() {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Void> disconnect() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public PlatformStatus status() {
            return PlatformStatus.connected(System.currentTimeMillis());
        }

        @Override
        public String platformId() {
            return id;
        }
    }

    static class FactoryA implements PlatformConnectionFactory {
        @Override
        public String platformId() {
            return "a";
        }

        @Override
        public PlatformConnection create() {
            return new TestConn(platformId());
        }

        @Override
        public PlatformMetadata metadata() {
            return new PlatformMetadata(platformId(), "Alpha", "1", Set.of(Capability.EVENTS));
        }
    }

    static class FactoryB implements PlatformConnectionFactory {
        @Override
        public String platformId() {
            return "b";
        }

        @Override
        public PlatformConnection create() {
            return new TestConn(platformId());
        }

        @Override
        public PlatformMetadata metadata() {
            return new PlatformMetadata(platformId(), "Beta", "1", Set.of(Capability.EVENTS, Capability.CHAT));
        }
    }

    @Test
    void testMetadataSnapshotContainsAll() {
        PlatformConnectionRegistry reg = new PlatformConnectionRegistry(List.of(new FactoryA(), new FactoryB()));
        // Filter only the ones we injected (ignore any SPI extras)
        Set<String> ids = reg.platformIds().stream().filter(id -> id.equals("a") || id.equals("b"))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("a", "b"), ids);
        assertEquals(2, reg.metadataSnapshot().entrySet().stream()
                .filter(e -> e.getKey().equals("a") || e.getKey().equals("b")).count());
        assertTrue(reg.metadata("b").get().capabilities().contains(Capability.CHAT));
    }
}
