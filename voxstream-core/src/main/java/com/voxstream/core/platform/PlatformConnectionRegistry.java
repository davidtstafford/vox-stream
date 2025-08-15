package com.voxstream.core.platform;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.voxstream.platform.api.PlatformConnection;
import com.voxstream.platform.api.PlatformConnectionFactory;
import com.voxstream.platform.api.PlatformMetadata;
import com.voxstream.platform.api.PlatformStatus;

/**
 * Registry that holds discovered PlatformConnectionFactory beans and lazily
 * instantiates singleton connections per platform id for now (may evolve to
 * multi-account support later).
 *
 * <p>
 * Phase 3.4 enhancement: also loads {@link PlatformConnectionFactory}
 * implementations via Java SPI (ServiceLoader) as a fallback / plugin
 * mechanism. Spring-discovered beans take precedence; SPI-loaded instances are
 * added only for platform ids not already registered. This allows lightweight
 * connectors (e.g. dummy test connectors or future external plugins) to be
 * provided without Spring context wiring while still supporting full DI for
 * complex platforms (e.g. Twitch) when running inside Spring.
 * </p>
 */
@Component
public class PlatformConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(PlatformConnectionRegistry.class);

    private final Map<String, PlatformConnectionFactory> factories = new ConcurrentHashMap<>();
    private final Map<String, PlatformConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, PlatformMetadata> metadata = new ConcurrentHashMap<>();

    @Autowired
    public PlatformConnectionRegistry(Collection<PlatformConnectionFactory> discovered) {
        // 1. Register Spring-discovered factories first (higher precedence)
        discovered.forEach(f -> registerFactory(f, false));
        log.info("Discovered {} PlatformConnectionFactory Spring beans: {}", factories.size(), factories.keySet());
        // 2. SPI fallback loading (only adds if platform id not present already)
        loadSpiFactories();
    }

    private void loadSpiFactories() {
        try {
            ServiceLoader<PlatformConnectionFactory> loader = ServiceLoader.load(PlatformConnectionFactory.class);
            int added = 0;
            for (PlatformConnectionFactory f : loader) {
                if (factories.containsKey(f.platformId())) {
                    continue; // Spring bean already registered for this id
                }
                registerFactory(f, true);
                added++;
            }
            if (added > 0) {
                log.info("SPI loaded {} PlatformConnectionFactory implementations: {}", added, factories.keySet());
            } else {
                log.debug("No additional SPI PlatformConnectionFactory implementations loaded");
            }
        } catch (Exception e) {
            log.warn("SPI loading of PlatformConnectionFactory failed: {}", e.getMessage());
        }
    }

    private void registerFactory(PlatformConnectionFactory f, boolean spi) {
        factories.put(f.platformId(), f);
        try {
            metadata.put(f.platformId(), f.metadata());
        } catch (Exception e) {
            log.warn("Metadata build failed for {} (spi={}): {}", f.platformId(), spi, e.getMessage());
        }
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

    public Optional<PlatformMetadata> metadata(String platformId) {
        return Optional.ofNullable(metadata.get(platformId));
    }

    public Map<String, PlatformMetadata> metadataSnapshot() {
        return Map.copyOf(metadata);
    }
}
