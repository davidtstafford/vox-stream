package com.voxstream.core.platform;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;
import com.voxstream.platform.api.PlatformStatus;

/**
 * Registry that holds discovered PlatformConnectionFactory beans and lazily
 * instantiates singleton connections per platform id for now (may evolve to
 * multi-account support later).
 */
@Component
public class PlatformConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(PlatformConnectionRegistry.class);

    private final Map<String, PlatformConnectionFactory> factories = new ConcurrentHashMap<>();
    private final Map<String, PlatformConnection> connections = new ConcurrentHashMap<>();

    @Autowired
    public PlatformConnectionRegistry(Collection<PlatformConnectionFactory> discovered) {
        discovered.forEach(f -> factories.put(f.platformId(), f));
        log.info("Discovered {} PlatformConnectionFactory beans: {}", factories.size(), factories.keySet());
    }

    public Optional<PlatformConnection> get(String platformId) {
        PlatformConnectionFactory factory = factories.get(platformId);
        if (factory == null)
            return Optional.empty();
        return Optional.of(connections.computeIfAbsent(platformId, id -> factory.create()));
    }

    public Map<String, PlatformStatus> snapshotStatuses() {
        Map<String, PlatformStatus> snapshot = new ConcurrentHashMap<>();
        factories.keySet().forEach(id -> {
            PlatformConnection conn = connections.get(id);
            snapshot.put(id, conn != null ? conn.status() : PlatformStatus.disconnected());
        });
        return snapshot;
    }

    public Collection<String> platformIds() {
        return factories.keySet();
    }
}
