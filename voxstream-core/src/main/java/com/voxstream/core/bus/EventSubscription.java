package com.voxstream.core.bus;

import java.util.Objects;
import java.util.function.Consumer;

import com.voxstream.core.event.Event;

/**
 * Subscription descriptor with filter, consumer, and priority.
 */
public class EventSubscription {
    private final EventFilter filter;
    private final Consumer<Event> consumer;
    private final int priority; // higher first

    public EventSubscription(EventFilter filter, Consumer<Event> consumer, int priority) {
        this.filter = filter != null ? filter : EventFilter.acceptAll();
        this.consumer = Objects.requireNonNull(consumer);
        this.priority = priority;
    }

    public EventFilter getFilter() {
        return filter;
    }

    public Consumer<Event> getConsumer() {
        return consumer;
    }

    public int getPriority() {
        return priority;
    }
}
