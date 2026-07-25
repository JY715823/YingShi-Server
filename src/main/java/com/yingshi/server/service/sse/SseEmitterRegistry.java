package com.yingshi.server.service.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理 libraryId -> SseEmitter 连接的注册表。
 *
 * 设计要点：
 * - 每个连接绑定一个 userId，用于排除 actor 自己的连接（与 FCM partner-only 设计一致）
 * - 使用 ConcurrentHashMap 保证线程安全
 * - 提供 sendToPartners() 方法：向 library 内所有非 actor 连接推送事件
 * - 定时发送心跳（:heartbeat 注释行），客户端据此判断连接存活性
 * - R3-DIST-003: 事件ID + ring buffer for Last-Event-ID replay
 */
@Component
public class SseEmitterRegistry {
    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /** R3-DIST-003: Monotonic event counter for event IDs. */
    private final AtomicLong eventCounter = new AtomicLong(0);

    /** R3-DIST-003: Ring buffer of recent events for Last-Event-ID replay (max 200 events). */
    private static final int EVENT_BUFFER_SIZE = 200;
    private final ConcurrentLinkedDeque<BufferedEvent> eventBuffer = new ConcurrentLinkedDeque<>();

    private record Connection(String connectionId, String userId, SseEmitter emitter) {}

    /** R3-DIST-003: Buffered event for Last-Event-ID replay. */
    private record BufferedEvent(long eventId, String libraryId, Map<String, String> data) {}

    // libraryId -> (connectionId -> Connection)
    private final Map<String, Map<String, Connection>> connectionsByLibrary = new ConcurrentHashMap<>();

    /**
     * 注册一个新的 SSE 连接。
     * 调用方在 SseController 中创建 SseEmitter 并返回给客户端。
     */
    public SseEmitter register(String libraryId, String userId, long timeoutMillis) {
        String connectionId = java.util.UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        Connection connection = new Connection(connectionId, userId, emitter);

        connectionsByLibrary
            .computeIfAbsent(libraryId, k -> new ConcurrentHashMap<>())
            .put(connectionId, connection);

        emitter.onCompletion(() -> unregister(libraryId, connectionId));
        emitter.onTimeout(() -> {
            log.info("SSE connection timed out: libraryId={}, userId={}, connectionId={}",
                libraryId, userId, connectionId);
            emitter.complete();
            unregister(libraryId, connectionId);
        });
        emitter.onError(ex -> {
            log.info("SSE connection error: libraryId={}, userId={}, connectionId={}, error={}",
                libraryId, userId, connectionId, ex.getMessage());
            unregister(libraryId, connectionId);
        });

        log.info("SSE connection registered: libraryId={}, userId={}, connectionId={}, totalConnections={}",
            libraryId, userId, connectionId, totalConnections());
        return emitter;
    }

    public void unregister(String libraryId, String connectionId) {
        Map<String, Connection> libraryConnections = connectionsByLibrary.get(libraryId);
        if (libraryConnections == null) return;
        Connection removed = libraryConnections.remove(connectionId);
        if (removed != null) {
            log.info("SSE connection unregistered: libraryId={}, userId={}, connectionId={}",
                libraryId, removed.userId(), connectionId);
        }
        if (libraryConnections.isEmpty()) {
            connectionsByLibrary.remove(libraryId, libraryConnections);
        }
    }

    /**
     * 向 library 内所有非 actor 的连接推送事件数据。
     * 与 FCM targetTokensFor() 的 partner-only 设计保持一致。
     * 无论 FCM token 是否可用，SSE 都会尝试推送（SSE 不依赖 FCM）。
     */
    public void sendToPartners(String libraryId, String actorUserId, Map<String, String> data) {
        Map<String, Connection> libraryConnections = connectionsByLibrary.get(libraryId);
        if (libraryConnections == null || libraryConnections.isEmpty()) {
            log.info("SSE sendToPartners: no connections for libraryId={}, skipping", libraryId);
            return;
        }

        // R3-DIST-003: Assign monotonic event ID and buffer for replay
        long eventId = eventCounter.incrementAndGet();
        bufferEvent(new BufferedEvent(eventId, libraryId, data));

        int sent = 0;
        int skippedActor = 0;
        int failed = 0;
        for (Connection connection : libraryConnections.values()) {
            if (actorUserId != null && actorUserId.equals(connection.userId())) {
                skippedActor++;
                continue;
            }
            try {
                connection.emitter().send(
                    SseEmitter.event()
                        .id(String.valueOf(eventId))
                        .name("message")
                        .data(data, MediaType.APPLICATION_JSON)
                );
                sent++;
            } catch (IOException | IllegalStateException ex) {
                log.warn("SSE send failed, unregistering: libraryId={}, connectionId={}, userId={}, error={}",
                    libraryId, connection.connectionId(), connection.userId(), ex.getMessage());
                unregister(libraryId, connection.connectionId());
                failed++;
            }
        }
        log.info("SSE sendToPartners: libraryId={}, eventId={}, total={}, sent={}, skippedActor={}, failed={}",
            libraryId, eventId, libraryConnections.size(), sent, skippedActor, failed);
    }

    /**
     * R3-DIST-003: Buffer an event for Last-Event-ID replay.
     * Maintains a ring buffer of EVENT_BUFFER_SIZE most recent events.
     */
    private void bufferEvent(BufferedEvent event) {
        eventBuffer.addLast(event);
        while (eventBuffer.size() > EVENT_BUFFER_SIZE) {
            eventBuffer.pollFirst();
        }
    }

    /**
     * R3-DIST-003: Replay missed events to a newly connected client.
     * Sends all buffered events with eventId > lastEventId for the given library.
     */
    public void replayEvents(String libraryId, long lastEventId, SseEmitter emitter) {
        int replayed = 0;
        for (BufferedEvent event : eventBuffer) {
            if (event.eventId() > lastEventId && libraryId.equals(event.libraryId())) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .id(String.valueOf(event.eventId()))
                            .name("message")
                            .data(event.data(), MediaType.APPLICATION_JSON)
                    );
                    replayed++;
                } catch (IOException | IllegalStateException ex) {
                    log.warn("SSE replay failed: libraryId={}, eventId={}", libraryId, event.eventId());
                    break;
                }
            }
        }
        if (replayed > 0) {
            log.info("SSE replay: libraryId={}, lastEventId={}, replayed={}", libraryId, lastEventId, replayed);
        }
    }

    /**
     * 定时心跳：每 30 秒向所有连接发送 :heartbeat 注释行。
     * 客户端收到后更新 lastHeartbeatAt，超过 90s 未收到则强制重连。
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void sendHeartbeats() {
        if (connectionsByLibrary.isEmpty()) return;
        int totalSent = 0;
        int totalFailed = 0;
        for (Map.Entry<String, Map<String, Connection>> libraryEntry : connectionsByLibrary.entrySet()) {
            String libraryId = libraryEntry.getKey();
            for (Connection connection : libraryEntry.getValue().values()) {
                try {
                    connection.emitter().send(SseEmitter.event().comment("heartbeat"));
                    totalSent++;
                } catch (IOException | IllegalStateException ex) {
                    unregister(libraryId, connection.connectionId());
                    totalFailed++;
                }
            }
        }
        if (totalSent > 0 || totalFailed > 0) {
            log.info("SSE heartbeat: sent={}, failed={}, totalConnections={}",
                totalSent, totalFailed, totalConnections());
        }
    }

    public int totalConnections() {
        return connectionsByLibrary.values().stream().mapToInt(Map::size).sum();
    }

    public int connectionCount(String libraryId) {
        Map<String, Connection> libraryConnections = connectionsByLibrary.get(libraryId);
        return libraryConnections == null ? 0 : libraryConnections.size();
    }
}
