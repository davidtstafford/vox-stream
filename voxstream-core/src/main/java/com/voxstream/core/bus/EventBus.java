package com.voxstream.core.bus;

import java.util.List;
import java.util.Optional;

import com.voxstream.core.event.Event;

/**
 * EventBus contract.
 */
public interface EventBus {
    void publish(Event event);

    SubscriptionHandle subscribe(EventSubscription subscription);

    void unsubscribe(SubscriptionHandle handle);

    // For future querying / metrics
    default Optional<Object> getMetricsSnapshot() {
        return Optional.empty();
    }

    default List<Event> recentEvents(int limit) {
        return List.of();
    }
}
