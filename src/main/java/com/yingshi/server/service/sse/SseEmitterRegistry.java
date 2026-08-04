package com.yingshi.server.service.sse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.domain.notification.NotificationEventEntity;
import com.yingshi.server.repository.NotificationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 管理 libraryId -> SseEmitter 连接的注册表。
 *
 * 设计要点：
 * - 每个连接绑定一个 userId，用于排除 actor 自己的连接（与 FCM partner-only 设计一致）
 * - 使用 ConcurrentHashMap 保证线程安全
 * - 提供 sendToPartners() 方法：向 library 内所有非 actor 连接推送事件
 * - 定时发送心跳（:heartbeat 注释行），客户端据此判断连接存活性
 *
 * R2-A-1: 持久化事件表替代纯内存 ring buffer
 * - SSE event id 现在使用 notification_events 表的 BIGSERIAL id（持久化、单调递增）
 * - 重启后客户端仍可通过 Last-Event-ID 补拉丢失事件（之前 AtomicLong 重启后重置）
 * - 内存 ring buffer 仍保留作为短期缓存（避免每次 replay 都打 DB）
 */
@Component
public class SseEmitterRegistry {
    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /** R3-DIST-003: Ring buffer of recent events for fast in-memory replay (max 200 events). */
    private static final int EVENT_BUFFER_SIZE = 200;
    private final ConcurrentLinkedDeque<BufferedEvent> eventBuffer = new ConcurrentLinkedDeque<>();

    private record Connection(String connectionId, String userId, SseEmitter emitter) {}

    /** R3-DIST-003: Buffered event for fast in-memory Last-Event-ID replay. */
    private record BufferedEvent(long eventId, String libraryId, Map<String, String> data) {}

    // libraryId -> (connectionId -> Connection)
    private final Map<String, Map<String, Connection>> connectionsByLibrary = new ConcurrentHashMap<>();

    private final NotificationEventRepository notificationEventRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SseEmitterRegistry(NotificationEventRepository notificationEventRepository,
                              org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.notificationEventRepository = notificationEventRepository;
        // REQUIRES_NEW 是本类的关键设计：sendToPartners 常在事务的 afterCommit 回调中被调用
        // （PushNotificationService.sendSseAfterCommit）。此时外层事务的 JDBC 连接已提交，但
        // EntityManagerHolder 的 transactionActive 标志尚未清理（cleanup 在 afterCompletion 之后），
        // 若用默认 REQUIRED 传播会误加入这个"僵尸事务"，saveAndFlush 抛 "No active transaction"，
        // 导致 life 模块推送整体失败（生产事故根因）。REQUIRES_NEW 挂起僵尸上下文、开启并独立提交
        // 新事务，在任何调用时机都可靠落库。
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

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
     *
     * R2-A-1: 同时将事件持久化到 notification_events 表，使用其 BIGSERIAL id
     * 作为 SSE event id（替代之前的 AtomicLong 自增计数器，server 重启后仍可补拉）。
     *
     * 注意：本方法刻意不加 @Transactional——持久化由 persistEvent 内部的
     * REQUIRES_NEW TransactionTemplate 自主管理。加 @Transactional 会在 afterCommit
     * 回调场景下误加入已提交的僵尸事务，导致 "No active transaction" 异常。
     */
    public void sendToPartners(String libraryId, String actorUserId, Map<String, String> data) {
        // BUG-1 fix: Always persist event FIRST, regardless of active connections.
        // Previously, when no connections existed the method returned early without persisting,
        // causing events to be permanently lost (no replay, no notification list entry).
        Long eventId = persistEvent(libraryId, actorUserId, data);
        if (eventId == null) {
            log.warn("sendToPartners: persistEvent returned null, skipping SSE send for libraryId={}", libraryId);
            return;
        }

        // Cache in memory ring buffer for fast in-memory replay path.
        bufferEvent(new BufferedEvent(eventId.longValue(), libraryId, data));

        Map<String, Connection> libraryConnections = connectionsByLibrary.get(libraryId);
        if (libraryConnections == null || libraryConnections.isEmpty()) {
            log.info("SSE sendToPartners: no active connections for libraryId={}, event persisted (id={}) for replay",
                libraryId, eventId);
            return;
        }

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
     * R2-A-1: Persist event to notification_events table.
     * Returns the database BIGSERIAL id used as the SSE event id.
     * Idempotent: if (eventId, libraryId, userId) already exists, reuses that id.
     */
    private Long persistEvent(String libraryId, String userId, Map<String, String> data) {
        return transactionTemplate.execute(status -> {
            String businessEventId = data != null ? data.get("notificationId") : null;
            String type = data != null ? data.get("type") : null;
            if (businessEventId == null || businessEventId.isBlank()) {
                businessEventId = "sse:" + libraryId + ":" + System.currentTimeMillis();
            }
            String jsonData;
            try {
                jsonData = objectMapper.writeValueAsString(data == null ? Map.of() : data);
            } catch (Exception e) {
                log.warn("Failed to serialize SSE data for persistence: {}", e.getMessage());
                jsonData = "{}";
            }
            // Idempotent: reuse existing record if present
            NotificationEventEntity existing = notificationEventRepository
                    .findByEventIdAndLibraryIdAndUserId(businessEventId, libraryId, userId)
                    .orElse(null);
            if (existing != null) {
                return existing.getId();
            }
            NotificationEventEntity entity = new NotificationEventEntity();
            entity.setLibraryId(libraryId);
            entity.setUserId(userId);
            entity.setEventId(businessEventId);
            entity.setType(type == null ? "message" : type);
            entity.setData(jsonData);
            NotificationEventEntity saved = notificationEventRepository.saveAndFlush(entity);
            Long savedId = saved.getId();
            if (savedId == null) {
                log.warn("persistEvent: saved entity has null id, event_id={}, libraryId={}, userId={}", businessEventId, libraryId, userId);
                return null;
            }
            return savedId;
        });
    }

    /**
     * R3-DIST-003: Buffer an event for fast in-memory Last-Event-ID replay.
     * Maintains a ring buffer of EVENT_BUFFER_SIZE most recent events.
     */
    private void bufferEvent(BufferedEvent event) {
        eventBuffer.addLast(event);
        while (eventBuffer.size() > EVENT_BUFFER_SIZE) {
            eventBuffer.pollFirst();
        }
    }

    /**
     * R2-A-1: Replay missed events to a newly connected client.
     *
     * <p>Query strategy:
     * <ol>
     *   <li>Try the in-memory ring buffer first (fast path, covers transient disconnects)</li>
     *   <li>Always fall back to the persistent event table (covers server restart scenarios
     *       where the ring buffer was lost, or when lastEventId is older than the buffer)</li>
     * </ol>
     *
     * @param libraryId   library scope
     * @param lastEventId last SSE event id the client received (BIGSERIAL id from notification_events)
     * @param emitter     target SSE emitter to send replayed events to
     */
    public void replayEvents(String libraryId, long lastEventId, SseEmitter emitter) {
        int replayed = 0;
        // BUG-2 fix: Track eventIds already sent from ring buffer to avoid duplicates from DB path.
        java.util.Set<Long> sentIds = new java.util.HashSet<>();

        // Fast path: in-memory ring buffer (covers most reconnect cases)
        for (BufferedEvent event : eventBuffer) {
            if (event.eventId() > lastEventId && libraryId.equals(event.libraryId())) {
                if (sendReplay(emitter, event.eventId(), event.data(), libraryId)) {
                    sentIds.add(event.eventId());
                    replayed++;
                } else {
                    break;
                }
            }
        }

        // R2-A-1: Persistent path — query event table for any events with id > lastEventId
        // that were NOT already replayed from the ring buffer (e.g. server restart,
        // or older than buffer window). This is what makes replay survive server restarts.
        if (lastEventId > 0) {
            try {
                List<NotificationEventEntity> missed = notificationEventRepository
                        .findByLibraryIdAndIdGreaterThanOrderByIdAsc(libraryId, lastEventId);
                for (NotificationEventEntity entity : missed) {
                    // BUG-2 fix: Skip events already sent from ring buffer
                    if (sentIds.contains(entity.getId())) continue;
                    Map<String, String> data = deserializeData(entity.getData());
                    if (sendReplay(emitter, entity.getId(), data, libraryId)) {
                        replayed++;
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("SSE replay: failed to query event table: libraryId={}, lastEventId={}, error={}",
                        libraryId, lastEventId, e.getMessage());
            }
        }

        if (replayed > 0) {
            log.info("SSE replay: libraryId={}, lastEventId={}, replayed={}", libraryId, lastEventId, replayed);
        }
    }

    private boolean sendReplay(SseEmitter emitter, long eventId, Map<String, String> data, String libraryId) {
        try {
            emitter.send(
                SseEmitter.event()
                    .id(String.valueOf(eventId))
                    .name("message")
                    .data(data, MediaType.APPLICATION_JSON)
            );
            return true;
        } catch (IOException | IllegalStateException ex) {
            log.warn("SSE replay failed: libraryId={}, eventId={}", libraryId, eventId);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize SSE event data: {}", e.getMessage());
            return Map.of();
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
