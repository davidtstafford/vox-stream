package com.voxstream.core.bus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.voxstream.core.config.VoxStreamConfiguration;
import com.voxstream.core.event.Event;
import com.voxstream.core.persistence.EventPersistenceService;
import com.voxstream.core.service.CoreErrorHandler;

/**
 * In-memory EventBus implementation (Phase 2.1 skeleton).
 */
@Service
public class EventBusImpl implements EventBus, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(EventBusImpl.class);

    private final ConcurrentLinkedQueue<Event> buffer = new ConcurrentLinkedQueue<>();
    private final Map<String, EventSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, SubscriptionHandle> handles = new ConcurrentHashMap<>();

    private final VoxStreamConfiguration configuration;
    private final CoreErrorHandler coreErrorHandler;
    private final EventPersistenceService persistenceService;
    private final EventMetrics metrics = new EventMetrics();

    private final ExecutorService dispatcherPool;
    private final ScheduledExecutorService maintenanceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "event-bus-maintenance");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final BackpressureStrategy backpressureStrategy;
    private final int maxEvents;

    @Autowired
    public EventBusImpl(VoxStreamConfiguration configuration,
            CoreErrorHandler coreErrorHandler,
            EventPersistenceService persistenceService) {
        this.configuration = configuration;
        this.coreErrorHandler = coreErrorHandler;
        this.persistenceService = persistenceService;
        this.maxEvents = configuration.getEventBus().getEffectiveBufferSize();
        this.backpressureStrategy = parseStrategy(configuration.getEventBus().getBackpressureStrategy());

        int threads = Math.max(1, configuration.getEventBus().getDispatcherThreads());
        this.dispatcherPool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "event-dispatcher");
            t.setDaemon(true);
            return t;
        });

        startMaintenance();
        logger.info("EventBus initialized with bufferSize={}, threads={}, strategy={}", maxEvents, threads,
                backpressureStrategy);
    }

    private BackpressureStrategy parseStrategy(String v) {
        try {
            return BackpressureStrategy.valueOf(v.toUpperCase());
        } catch (Exception e) {
            return BackpressureStrategy.DROP_OLDEST;
        }
    }

    @Override
    public void publish(Event event) {
        if (!running.get() || event == null)
            return;

        metrics.incPublished();

        // Backpressure handling
        if (buffer.size() >= maxEvents) {
            switch (backpressureStrategy) {
                case DROP_NEW:
                    metrics.incDropped();
                    logger.debug("Dropping new event due to full buffer: {}", event.getId());
                    return;
                case DROP_OLDEST:
                    buffer.poll();
                    metrics.incDropped();
                    break;
                case BLOCK:
                    // Busy-wait simple block (not ideal, but acceptable for skeleton)
                    while (buffer.size() >= maxEvents && running.get()) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    break;
            }
        }

        buffer.offer(event);
        // Dispatch asynchronously
        dispatcherPool.submit(() -> dispatch(event));

        // Persistence (stub toggle)
        if (configuration.getEventBus().isEnablePersistence()) {
            try {
                persistenceService.save(event);
                metrics.incPersistenceSuccess();
            } catch (Exception e) {
                metrics.incPersistenceFailure();
                coreErrorHandler.handleError("Persistence save failed", e);
            }
        }
    }

    private void dispatch(Event event) {
        long start = System.nanoTime();
        List<Map.Entry<String, EventSubscription>> subs = new ArrayList<>(subscriptions.entrySet());
        // Sort by priority descending
        subs.sort(Comparator.comparingInt(e -> -e.getValue().getPriority()));

        for (Map.Entry<String, EventSubscription> entry : subs) {
            EventSubscription sub = entry.getValue();
            try {
                if (sub.getFilter().test(event)) {
                    sub.getConsumer().accept(event);
                    metrics.incDelivered(System.nanoTime() - start);
                } else {
                    metrics.incFiltered();
                }
            } catch (Exception ex) {
                coreErrorHandler.handleError("Subscriber execution failed", ex);
            }
        }
    }

    @Override
    public SubscriptionHandle subscribe(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription");
        SubscriptionHandle handle = new SubscriptionHandle();
        subscriptions.put(handle.getId(), subscription);
        handles.put(handle.getId(), handle);
        logger.debug("Subscription added: {} priority={}", handle.getId(), subscription.getPriority());
        return handle;
    }

    @Override
    public void unsubscribe(SubscriptionHandle handle) {
        if (handle == null)
            return;
        subscriptions.remove(handle.getId());
        handles.remove(handle.getId());
        logger.debug("Subscription removed: {}", handle.getId());
    }

    @Override
    public Optional<Object> getMetricsSnapshot() {
        return Optional.of(metrics.toString());
    }

    @Override
    public List<Event> recentEvents(int limit) {
        List<Event> list = new ArrayList<>(buffer);
        int size = list.size();
        if (limit >= size)
            return list;
        return list.subList(size - limit, size);
    }

    private void startMaintenance() {
        int purgeMinutes = configuration.getEventBus().getPurgeIntervalMinutes();
        maintenanceScheduler.scheduleAtFixedRate(this::purgeTask, purgeMinutes, purgeMinutes, TimeUnit.MINUTES);
    }

    private void purgeTask() {
        try {
            // Remove expired events
            int removed = 0;
            List<Event> retained = new ArrayList<>();
            Event ev;
            while ((ev = buffer.poll()) != null) {
                if (ev.isExpired() || buffer.size() > maxEvents) {
                    removed++;
                } else {
                    retained.add(ev);
                }
            }
            buffer.addAll(retained);
            if (removed > 0) {
                logger.debug("Purge removed {} events", removed);
            }
        } catch (Exception e) {
            coreErrorHandler.handleError("Purge task failed", e);
        }
    }

    // Visible for testing: trigger maintenance cycle immediately without waiting
    // for
    // the scheduled interval.
    void maintenanceTick() {
        purgeTask();
    }

    @Override
    public void destroy() {
        running.set(false);
        dispatcherPool.shutdown();
        maintenanceScheduler.shutdown();
    }
}
