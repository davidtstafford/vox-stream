package com.voxstream.core.twitch.eventsub;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal EventSub WebSocket client (Phase 3.2 MVP). Handles connect, heartbeat
 * watchdog,
 * reconnect callback (delegated to higher-level connection), and message
 * dispatch.
 */
public class EventSubWebSocketClient implements EventSubClient {

    private static final Logger log = LoggerFactory.getLogger(EventSubWebSocketClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "twitch-eventsub");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket socket;
    private volatile Instant lastHeartbeat = Instant.now();
    private volatile boolean closed;

    private final CopyOnWriteArrayList<Consumer<EventSubMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> closeListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> heartbeatTimeoutListeners = new CopyOnWriteArrayList<>();

    @Override
    public void addMessageListener(Consumer<EventSubMessage> l) {
        if (l != null)
            messageListeners.add(l);
    }

    @Override
    public void addCloseListener(Runnable r) {
        if (r != null)
            closeListeners.add(r);
    }

    @Override
    public void addHeartbeatTimeoutListener(Runnable r) {
        if (r != null)
            heartbeatTimeoutListeners.add(r);
    }

    @Override
    public CompletableFuture<Void> connect(String url, int heartbeatIntervalSec) {
        Objects.requireNonNull(url);
        CompletableFuture<Void> fut = new CompletableFuture<>();
        http.newWebSocketBuilder().buildAsync(URI.create(url), new Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                socket = webSocket;
                lastHeartbeat = Instant.now();
                fut.complete(null);
                Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                try {
                    JsonNode root = mapper.readTree(data.toString());
                    String metaType = root.path("metadata").path("message_type")
                            .asText(root.path("type").asText("event"));
                    if ("session_keepalive".equalsIgnoreCase(metaType)
                            || "session_welcome".equalsIgnoreCase(metaType)) {
                        lastHeartbeat = Instant.now();
                    }
                    String subType = root.path("payload").path("subscription").path("type").asText(null);
                    String subVer = root.path("payload").path("subscription").path("version").asText(null);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) mapper.convertValue(root, Map.class);
                    EventSubMessage msg = new EventSubMessage(metaType, subType, subVer, Instant.now(), payload);
                    for (var l : messageListeners) {
                        try {
                            l.accept(msg);
                        } catch (Exception ignore) {
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse EventSub message: {}", e.toString());
                }
                return Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                for (var r : closeListeners) {
                    try {
                        r.run();
                    } catch (Exception ignore) {
                    }
                }
                return Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.warn("EventSub WS error: {}", error.toString());
            }
        });
        // Heartbeat watchdog task
        scheduler.scheduleAtFixedRate(() -> {
            if (closed)
                return;
            if (heartbeatIntervalSec <= 0)
                return;
            Instant last = lastHeartbeat;
            if (Instant.now().isAfter(last.plusSeconds(Math.max(5, heartbeatIntervalSec * 2L)))) {
                log.warn("EventSub heartbeat timeout detected");
                for (var r : heartbeatTimeoutListeners) {
                    try {
                        r.run();
                    } catch (Exception ignore) {
                    }
                }
            }
        }, heartbeatIntervalSec, heartbeatIntervalSec, TimeUnit.SECONDS);
        return fut;
    }

    @Override
    public boolean isOpen() {
        return socket != null;
    }

    @Override
    public void send(String text) {
        WebSocket s = socket;
        if (s != null)
            s.sendText(text, true);
    }

    /** Test hook to simulate heartbeat timeout without waiting. */
    void simulateHeartbeatTimeoutForTest() {
        for (var r : heartbeatTimeoutListeners) {
            try {
                r.run();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        WebSocket s = socket;
        if (s != null)
            try {
                s.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignore) {
            }
        scheduler.shutdownNow();
    }
}
