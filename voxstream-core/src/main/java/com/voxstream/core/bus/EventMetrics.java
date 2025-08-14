package com.voxstream.core.bus;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Basic metrics container for EventBus.
 */
public class EventMetrics {
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong filtered = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong totalDispatchNanos = new AtomicLong();

    public void incPublished() {
        published.incrementAndGet();
    }

    public void incDelivered(long nanos) {
        delivered.incrementAndGet();
        totalDispatchNanos.addAndGet(nanos);
    }

    public void incFiltered() {
        filtered.incrementAndGet();
    }

    public void incDropped() {
        dropped.incrementAndGet();
    }

    public long getPublished() {
        return published.get();
    }

    public long getDelivered() {
        return delivered.get();
    }

    public long getFiltered() {
        return filtered.get();
    }

    public long getDropped() {
        return dropped.get();
    }

    public double getAvgDispatchMicros() {
        long d = delivered.get();
        return d == 0 ? 0.0 : (totalDispatchNanos.get() / 1000.0) / d;
    }

    @Override
    public String toString() {
        return "EventMetrics{" +
                "published=" + published +
                ", delivered=" + delivered +
                ", filtered=" + filtered +
                ", dropped=" + dropped +
                ", avgDispatchMicros=" + String.format("%.2f", getAvgDispatchMicros()) +
                '}';
    }
}
