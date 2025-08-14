package com.voxstream.core.bus;

import com.voxstream.core.event.Event;

@FunctionalInterface
public interface EventFilter {
    boolean test(Event event);

    static EventFilter acceptAll() {
        return e -> true;
    }

    static EventFilter and(EventFilter a, EventFilter b) {
        return e -> a.test(e) && b.test(e);
    }

    static EventFilter or(EventFilter a, EventFilter b) {
        return e -> a.test(e) || b.test(e);
    }
}
