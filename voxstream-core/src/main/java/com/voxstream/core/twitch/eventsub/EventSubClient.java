package com.voxstream.core.twitch.eventsub;

import java.util.concurrent.CompletableFuture;

/** Abstraction of EventSub WebSocket client to enable mocking in tests. */
public interface EventSubClient extends AutoCloseable {
    void addMessageListener(java.util.function.Consumer<EventSubMessage> l);

    void addCloseListener(Runnable r);

    void addHeartbeatTimeoutListener(Runnable r);

    CompletableFuture<Void> connect(String url, int heartbeatIntervalSec);

    boolean isOpen();

    void send(String text);

    @Override
    void close();
}
