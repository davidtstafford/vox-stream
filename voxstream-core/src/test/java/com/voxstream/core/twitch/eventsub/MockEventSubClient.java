package com.voxstream.core.twitch.eventsub;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Test double for EventSubClient allowing manual message + heartbeat control.
 */
public class MockEventSubClient implements EventSubClient {
    private final CopyOnWriteArrayList<Consumer<EventSubMessage>> msgLs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> closeLs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> hbLs = new CopyOnWriteArrayList<>();
    private volatile boolean open;
    private final AtomicInteger connectCount = new AtomicInteger();

    @Override
    public void addMessageListener(Consumer<EventSubMessage> l) {
        if (l != null)
            msgLs.add(l);
    }

    @Override
    public void addCloseListener(Runnable r) {
        if (r != null)
            closeLs.add(r);
    }

    @Override
    public void addHeartbeatTimeoutListener(Runnable r) {
        if (r != null)
            hbLs.add(r);
    }

    @Override
    public CompletableFuture<Void> connect(String url, int heartbeatIntervalSec) {
        open = true;
        connectCount.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void send(String text) {
    }

    @Override
    public void close() {
        open = false;
        closeLs.forEach(Runnable::run);
    }

    // Test helpers
    public void fireHeartbeatTimeout() {
        hbLs.forEach(Runnable::run);
    }

    public void fireMessage(EventSubMessage m) {
        msgLs.forEach(l -> l.accept(m));
    }

    public int getConnectCount() {
        return connectCount.get();
    }
}
