package com.voxstream.core.bus;

import java.util.UUID;

/**
 * Handle returned to caller for unsubscription.
 */
public class SubscriptionHandle {
    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "SubscriptionHandle{" + id + '}';
    }
}
