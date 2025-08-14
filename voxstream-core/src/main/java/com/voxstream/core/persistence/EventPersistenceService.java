package com.voxstream.core.persistence;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.voxstream.core.event.Event;

/**
 * Stub persistence service for events. Real implementation will arrive in Phase
 * 2.2.
 */
@Service
public class EventPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(EventPersistenceService.class);

    public void save(Event event) {
        // Stub
        logger.trace("Persist (stub) event {}", event.getId());
    }

    public List<Event> findRecent(int limit) {
        // Stub
        return List.of();
    }

    public int purgeOlderThan(long epochMillis) {
        // Stub
        return 0;
    }
}
