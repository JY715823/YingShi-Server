package com.yingshi.server.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.BowelEventEntity;
import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.NotificationReadEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.TrashItemState;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.domain.notification.NotificationEventEntity;
import com.yingshi.server.domain.notification.NotificationReadWaterlineEntity;
import com.yingshi.server.dto.notification.NotificationDto;
import com.yingshi.server.dto.notification.NotificationMarkAllReadResponse;
import com.yingshi.server.dto.notification.NotificationMediaItemDto;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.CommentRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.NotificationEventRepository;
import com.yingshi.server.repository.NotificationReadRepository;
import com.yingshi.server.repository.NotificationReadWaterlineRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.repository.UserRepository;
import com.yingshi.server.domain.PostMediaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final TrashItemRepository trashItemRepository;
    private final UploadTaskRepository uploadTaskRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final NotificationReadWaterlineRepository notificationReadWaterlineRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final BowelEventRepository bowelEventRepository;
    private final AlbumRepository albumRepository;
    private final PostMediaRepository postMediaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            TrashItemRepository trashItemRepository,
            UploadTaskRepository uploadTaskRepository,
            NotificationReadRepository notificationReadRepository,
            NotificationReadWaterlineRepository notificationReadWaterlineRepository,
            NotificationEventRepository notificationEventRepository,
            UserRepository userRepository,
            MediaRepository mediaRepository,
            BowelEventRepository bowelEventRepository,
            AlbumRepository albumRepository,
            PostMediaRepository postMediaRepository
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.trashItemRepository = trashItemRepository;
        this.uploadTaskRepository = uploadTaskRepository;
        this.notificationReadRepository = notificationReadRepository;
        this.notificationReadWaterlineRepository = notificationReadWaterlineRepository;
        this.notificationEventRepository = notificationEventRepository;
        this.userRepository = userRepository;
        this.mediaRepository = mediaRepository;
        this.bowelEventRepository = bowelEventRepository;
        this.albumRepository = albumRepository;
        this.postMediaRepository = postMediaRepository;
    }

    /**
     * R2-A-1: Persist a notification event to the {@code notification_events} table.
     *
     * <p>This is the durable backing store for SSE replay (replaces the in-memory
     * ring buffer that was lost on server restart). Idempotent: if an event with
     * the same (eventId, libraryId, userId) already exists, the existing record
     * is kept and no duplicate is inserted.
     *
     * <p>Called from SSE send paths (via SseEmitterRegistry) when an event is
     * pushed to a client, so the same event can be redelivered after reconnect.
     *
     * @param libraryId shared library scope
     * @param userId    recipient user (the partner, not the actor)
     * @param eventId   stable business event id (e.g. {@code "post:abc"})
     * @param type      event type (e.g. {@code "content_update"})
     * @param data      opaque JSON payload (SSE event data)
     */
    @Transactional
    public void recordEvent(String libraryId, String userId, String eventId, String type, String data) {
        if (libraryId == null || userId == null || eventId == null || eventId.isBlank()) {
            return;
        }
        Optional<NotificationEventEntity> existing = notificationEventRepository
                .findByEventIdAndLibraryIdAndUserId(eventId, libraryId, userId);
        if (existing.isPresent()) {
            return;
        }
        NotificationEventEntity entity = new NotificationEventEntity();
        entity.setLibraryId(libraryId);
        entity.setUserId(userId);
        entity.setEventId(eventId);
        entity.setType(type == null ? "message" : type);
        entity.setData(data == null ? "{}" : data);
        notificationEventRepository.save(entity);
    }

    /**
     * R2-A: List notifications from the persistent {@code notification_events} table.
     *
     * <p>Replaces the previous {@code collectNotificationEvents()} approach that
     * dynamically rebuilt notifications from 5 business tables (comments/posts/
     * trashItems/uploadTasks/bowelEvents) on every request. The events table is
     * populated by {@link com.yingshi.server.service.sse.SseEmitterRegistry#sendToPartners}
     * and {@link #recordEvent}, so reading from it gives a consistent view of
     * what was actually pushed.
     *
     * <p>Cursor format: {@code createdAtMillis:notificationId} (colon-separated,
     * compatible with the existing client contract). The {@code notificationId}
     * is the business {@code event_id} in the events table; it is resolved to
     * the BIGSERIAL {@code id} for efficient DB-level pagination.
     *
     * <p>Read state is determined by the read waterline
     * ({@code notification_read_waterline}) — events with {@code id <= waterline}
     * are read — plus per-notification {@code notification_reads} rows for
     * individual mark-read operations on events newer than the waterline.
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> listNotifications(AuthenticatedUser currentUser, Integer limit, String cursor) {
        int normalizedLimit = normalizeLimit(limit);
        String libraryId = currentUser.libraryId();
        String userId = currentUser.userId();

        List<NotificationEventEntity> entities = fetchEventsWithCursor(libraryId, cursor, normalizedLimit);
        if (entities.isEmpty()) {
            return List.of();
        }

        ReadStateResolver readState = buildReadStateResolver(userId, libraryId, entities);
        return entities.stream()
                .map(entity -> toDtoFromEvent(entity, currentUser, readState))
                .toList();
    }

    /**
     * R2-A: Get a single notification by id from the events table.
     *
     * <p>{@code notificationId} corresponds to the business {@code event_id}
     * column (not the BIGSERIAL {@code id}).
     */
    @Transactional(readOnly = true)
    public NotificationDto getNotification(String notificationId, AuthenticatedUser currentUser) {
        List<NotificationEventEntity> matches = notificationEventRepository
                .findByLibraryIdAndEventId(currentUser.libraryId(), notificationId);
        if (matches.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.NOT_FOUND,
                    "Notification was not found."
            );
        }
        NotificationEventEntity entity = matches.get(0);
        ReadStateResolver readState = buildReadStateResolver(currentUser.userId(), currentUser.libraryId(), List.of(entity));
        return toDtoFromEvent(entity, currentUser, readState);
    }

    /**
     * R2-A: Mark a single notification as read.
     *
     * <p>Writes a per-notification row to {@code notification_reads} (kept for
     * backward compatibility and individual mark-read). Does not advance the
     * waterline — that only happens on mark-all-read.
     */
    @Transactional
    public NotificationDto markRead(String notificationId, AuthenticatedUser currentUser) {
        NotificationDto notification = getNotification(notificationId, currentUser);
        ensureReadRecord(currentUser.userId(), notificationId);
        return withReadState(notification, true);
    }

    /**
     * R2-A: Mark all notifications as read for the current user.
     *
     * <p>Advances the read waterline to the newest event in the library, so
     * subsequent {@link #listNotifications} calls treat all events at or below
     * the waterline as read without per-row lookups. Also writes individual
     * {@code notification_reads} rows for events newer than the previous
     * waterline (backward-compat path for clients that still inspect that table).
     */
    @Transactional
    public NotificationMarkAllReadResponse markAllRead(AuthenticatedUser currentUser) {
        String libraryId = currentUser.libraryId();
        String userId = currentUser.userId();

        Long maxEventId = notificationEventRepository.findMaxIdByLibraryId(libraryId);
        if (maxEventId == null) {
            return new NotificationMarkAllReadResponse(true, 0);
        }

        Optional<NotificationReadWaterlineEntity> existingWaterline =
                notificationReadWaterlineRepository.findByUserIdAndLibraryId(userId, libraryId);
        Long previousWaterlineId = existingWaterline
                .map(NotificationReadWaterlineEntity::lastReadEventIdAsLong)
                .orElse(null);

        // Backward-compat: write notification_reads rows for events newer than
        // the previous waterline so legacy read-state lookups still work.
        int newlyReadCount = writeReadRecordsForNewEvents(userId, libraryId, previousWaterlineId);

        // Advance the waterline to the newest event.
        advanceWaterline(userId, libraryId, maxEventId);

        return new NotificationMarkAllReadResponse(true, newlyReadCount);
    }

    /**
     * R2-A: Fetch events from the events table with cursor-based pagination.
     *
     * <p>Cursor format: {@code createdAtMillis:notificationId}. The
     * {@code notificationId} is resolved to the event's BIGSERIAL {@code id}
     * via {@link NotificationEventRepository#findByLibraryIdAndEventId} for an
     * efficient {@code id < cursor} query. Falls back to timestamp-based
     * pagination if the cursor event has been purged by retention.
     */
    private List<NotificationEventEntity> fetchEventsWithCursor(String libraryId, String cursor, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        if (cursor == null || cursor.isBlank()) {
            return notificationEventRepository.findByLibraryIdOrderByIdDesc(libraryId, pageable);
        }
        // Cursor format: "createdAtMillis:notificationId" (colon-separated, client compat)
        String[] parts = cursor.split(":", 2);
        if (parts.length != 2) {
            return notificationEventRepository.findByLibraryIdOrderByIdDesc(libraryId, pageable);
        }
        try {
            long cursorMillis = Long.parseLong(parts[0]);
            String cursorEventId = parts[1];
            // Resolve cursor event_id -> BIGSERIAL id for efficient pagination.
            List<NotificationEventEntity> cursorEvents =
                    notificationEventRepository.findByLibraryIdAndEventId(libraryId, cursorEventId);
            if (!cursorEvents.isEmpty()) {
                Long cursorPkId = cursorEvents.get(0).getId();
                return notificationEventRepository.findByLibraryIdAndIdLessThanOrderByIdDesc(libraryId, cursorPkId, pageable);
            }
            // Fallback: cursor event was purged by retention — use timestamp.
            Instant cursorInstant = Instant.ofEpochMilli(cursorMillis);
            return notificationEventRepository.findByLibraryIdAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(libraryId, cursorInstant, pageable);
        } catch (NumberFormatException ignored) {
            return notificationEventRepository.findByLibraryIdOrderByIdDesc(libraryId, pageable);
        }
    }

    /**
     * R2-A: Build a read-state resolver that combines the waterline and the
     * per-notification {@code notification_reads} table.
     *
     * <p>An event is considered read if:
     * <ol>
     *   <li>the waterline exists and {@code event.id <= waterline.lastReadEventId}, OR</li>
     *   <li>the event's business id is in {@code notification_reads} (individual markRead), OR</li>
     *   <li>the current user is the actor (own actions are implicitly read).</li>
     * </ol>
     */
    private ReadStateResolver buildReadStateResolver(String userId, String libraryId, List<NotificationEventEntity> entities) {
        Long waterlineId = notificationReadWaterlineRepository
                .findByUserIdAndLibraryId(userId, libraryId)
                .map(NotificationReadWaterlineEntity::lastReadEventIdAsLong)
                .orElse(null);

        // Only need to check notification_reads for events newer than the waterline.
        List<String> idsToCheck = entities.stream()
                .filter(e -> waterlineId == null || e.getId() == null || e.getId() > waterlineId)
                .map(NotificationEventEntity::getEventId)
                .toList();
        Set<String> individuallyReadIds = idsToCheck.isEmpty()
                ? Set.of()
                : notificationReadRepository.findByUserIdAndNotificationIdIn(userId, idsToCheck).stream()
                        .map(NotificationReadEntity::getNotificationId)
                        .collect(Collectors.toSet());

        return new ReadStateResolver(waterlineId, individuallyReadIds);
    }

    /**
     * R2-A: Map a persistent event entity to a {@link NotificationDto}.
     *
     * <p>The {@code data} column is a JSONB string of {@code Map<String, String>}
     * written by {@code SseEmitterRegistry.persistEvent} / {@code recordEvent}.
     * Fields not present in the push payload (e.g. mediaItems, targetSummary)
     * are left null/empty — the events table stores what was pushed, which is
     * the essential notification content.
     */
    private NotificationDto toDtoFromEvent(NotificationEventEntity entity, AuthenticatedUser currentUser, ReadStateResolver readState) {
        Map<String, Object> data = deserializeEventData(entity.getData());
        String notificationId = entity.getEventId();
        String actorUserId = getStr(data, "actorUserId");
        boolean actorIsCurrentUser = currentUser.userId().equals(actorUserId);
        boolean isRead = readState.isRead(entity, actorIsCurrentUser);
        long createdAtMillis = entity.getCreatedAt() != null ? entity.getCreatedAt().toEpochMilli() : 0L;
        return new NotificationDto(
                notificationId,
                getStr(data, "type"),
                getStr(data, "module"),
                getStr(data, "category"),
                getStr(data, "title"),
                getStr(data, "body"),
                createdAtMillis,
                isRead,
                actorUserId,
                getStr(data, "actorDisplayName"),
                null, // actorAvatarUrl not carried in SSE payload
                actorIsCurrentUser,
                getStr(data, "groupId"),
                getStr(data, "operationId"),
                getInt(data, "groupItemCount"),
                List.of(), // mediaItems not carried in SSE payload
                getStr(data, "targetRoute"),
                getStr(data, "targetSummary"),
                getStr(data, "targetType"),
                getStr(data, "smallAlbumId"),
                getStr(data, "mediaId"),
                getStr(data, "trashItemId")
        );
    }

    /**
     * R2-A: Deserialize the JSONB {@code data} column into a {@code Map<String, Object>}.
     * Returns an empty map on failure (never null) so callers can safely use {@link #getStr}.
     */
    private Map<String, Object> deserializeEventData(String data) {
        if (data == null || data.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize notification event data: eventId={}, error={}", data, e.getMessage());
            return Map.of();
        }
    }

    private String getStr(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    private Integer getInt(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * R2-A: Write {@code notification_reads} rows for events newer than the
     * previous waterline (backward-compat for legacy read-state lookups).
     *
     * @return number of newly written rows
     */
    private int writeReadRecordsForNewEvents(String userId, String libraryId, Long previousWaterlineId) {
        // Fetch events newer than the previous waterline (or all if no waterline yet).
        List<NotificationEventEntity> newEvents;
        if (previousWaterlineId == null) {
            // No previous waterline: cap at MAX_LIMIT most recent events to bound work.
            newEvents = notificationEventRepository.findByLibraryIdOrderByIdDesc(
                    libraryId, PageRequest.of(0, MAX_LIMIT));
        } else {
            newEvents = notificationEventRepository.findByLibraryIdAndIdGreaterThanOrderByIdAsc(
                    libraryId, previousWaterlineId);
        }
        if (newEvents.isEmpty()) {
            return 0;
        }
        List<String> notificationIds = newEvents.stream()
                .map(NotificationEventEntity::getEventId)
                .toList();
        Set<String> alreadyRead = notificationReadRepository
                .findByUserIdAndNotificationIdIn(userId, notificationIds)
                .stream()
                .map(NotificationReadEntity::getNotificationId)
                .collect(Collectors.toSet());
        Instant now = Instant.now();
        List<NotificationReadEntity> newReads = notificationIds.stream()
                .filter(id -> !alreadyRead.contains(id))
                .map(id -> buildReadEntity(userId, id, now))
                .toList();
        if (!newReads.isEmpty()) {
            notificationReadRepository.saveAll(newReads);
        }
        return newReads.size();
    }

    /**
     * R2-A: Advance the read waterline to {@code maxEventId} (upsert).
     */
    private void advanceWaterline(String userId, String libraryId, Long maxEventId) {
        String maxEventIdStr = String.valueOf(maxEventId);
        Instant now = Instant.now();
        int updated = notificationReadWaterlineRepository.updateWaterline(userId, libraryId, maxEventIdStr, now);
        if (updated == 0) {
            NotificationReadWaterlineEntity entity = new NotificationReadWaterlineEntity();
            entity.setUserId(userId);
            entity.setLibraryId(libraryId);
            entity.setLastReadEventId(maxEventIdStr);
            entity.setLastReadAt(now);
            notificationReadWaterlineRepository.save(entity);
        }
    }

    /**
     * R2-A: Resolves the read state of an event using the waterline + per-row table.
     */
    private record ReadStateResolver(Long waterlineId, Set<String> individuallyReadIds) {
        boolean isRead(NotificationEventEntity entity, boolean actorIsCurrentUser) {
            if (actorIsCurrentUser) return true;
            if (waterlineId != null && entity.getId() != null && entity.getId() <= waterlineId) {
                return true;
            }
            return individuallyReadIds.contains(entity.getEventId());
        }
    }

    private List<NotificationEvent> collectNotificationEvents(AuthenticatedUser currentUser) {
        String libraryId = currentUser.libraryId();
        List<CommentEntity> comments = commentRepository.findByLibraryIdOrderByCreatedAtDesc(libraryId);
        List<PostEntity> posts = postRepository.findByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(libraryId);
        List<TrashItemEntity> trashItems = trashItemRepository.findByLibraryIdOrderByUpdatedAtDesc(libraryId);
        List<UploadTaskEntity> uploadTasks = uploadTaskRepository.findByLibraryIdOrderByUpdatedAtDesc(libraryId).stream()
                .filter(task -> task.getState() == UploadState.SUCCESS || task.getState() == UploadState.CANCELLED)
                .toList();
        List<BowelEventEntity> bowelEvents = bowelEventRepository.findTop50ByLibraryIdAndDeletedAtIsNullOrderByOccurredAtMillisDesc(libraryId);

        // Filter out posts belonging to life-console albums (人物痕迹, 吃饭, etc.).
        // These albums have includeInPhotoFeed=false and their own FCM push flow
        // via LifeConsoleService.notifyLifeConsoleChanged(). Generating a "小相册有内容更新"
        // notification for them causes wrong text and duplicates.
        Set<String> lifeConsoleAlbumIds = new HashSet<>();
        Set<String> postAlbumIds = posts.stream()
                .map(PostEntity::getAlbumId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (!postAlbumIds.isEmpty()) {
            albumRepository.findByLibraryIdAndIdIn(libraryId, postAlbumIds).stream()
                    .filter(album -> Boolean.FALSE.equals(album.getIncludeInPhotoFeed()))
                    .map(AlbumEntity::getId)
                    .forEach(lifeConsoleAlbumIds::add);
        }
        List<PostEntity> photoFeedPosts = posts.stream()
                .filter(post -> {
                    String albumId = post.getAlbumId();
                    if (albumId == null || albumId.isBlank()) return true;
                    return !lifeConsoleAlbumIds.contains(albumId);
                })
                .toList();

        NotificationSupportContext context = buildSupportContext(libraryId, comments, posts, trashItems, uploadTasks, bowelEvents);

        List<NotificationEvent> events = new ArrayList<>();
        comments.forEach(comment -> events.addAll(toCommentEvents(comment, currentUser, context)));
        photoFeedPosts.forEach(post -> events.add(toPostEvent(post, currentUser, context)));
        trashItems.forEach(item -> events.add(toTrashEvent(item, currentUser, context)));
        toUploadEvents(uploadTasks, currentUser, context).forEach(events::add);
        bowelEvents.forEach(event -> events.add(toBowelEventNotification(event, currentUser, context)));

        // Generate notification events for person/meal media uploads in life-console
        // albums.  These are essential for the SyncVersionTracker polling fallback
        // on devices where FCM is unreachable (e.g. no Google Play Services).
        if (!lifeConsoleAlbumIds.isEmpty()) {
            List<PostEntity> lifeConsolePosts = posts.stream()
                    .filter(post -> {
                        String albumId = post.getAlbumId();
                        return albumId != null && !albumId.isBlank() && lifeConsoleAlbumIds.contains(albumId);
                    })
                    .toList();
            if (!lifeConsolePosts.isEmpty()) {
                Map<String, AlbumEntity> lifeConsoleAlbums = albumRepository
                        .findByLibraryIdAndIdIn(libraryId, lifeConsoleAlbumIds).stream()
                        .collect(Collectors.toMap(AlbumEntity::getId, album -> album));
                for (PostEntity post : lifeConsolePosts) {
                    AlbumEntity album = lifeConsoleAlbums.get(post.getAlbumId());
                    if (album == null) continue;
                    String systemKey = album.getSystemKey();
                    if ("person".equals(systemKey) || "meal".equals(systemKey)) {
                        events.add(toLifeConsoleMediaEvent(post, systemKey, currentUser, context));
                    }
                }
            }
        }

        return events.stream()
                .sorted(Comparator.comparingLong(NotificationEvent::createdAtMillis).reversed()
                        .thenComparing(NotificationEvent::notificationId))
                .toList();
    }

    private List<NotificationDto> materializeNotifications(
            List<NotificationEvent> events,
            String userId,
            Integer limit,
            String cursor
    ) {
        // Step 1: cursor filtering (before limit truncation)
        // Cursor format: "createdAtMillis:notificationId" (composite key to avoid
        // losing items with the same timestamp)
        List<NotificationEvent> filteredEvents = events;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split(":", 2);
            if (parts.length == 2) {
                try {
                    long cursorTimestamp = Long.parseLong(parts[0]);
                    String cursorId = parts[1];
                    filteredEvents = events.stream()
                            .filter(e -> e.createdAtMillis() < cursorTimestamp
                                    || (e.createdAtMillis() == cursorTimestamp
                                    && e.notificationId().compareTo(cursorId) > 0))
                            .toList();
                } catch (NumberFormatException ignored) {
                    // Invalid cursor format, fall through to no filter
                }
            }
        }

        // Step 2: limit truncation
        List<NotificationEvent> visibleEvents = limit == null
                ? filteredEvents
                : filteredEvents.stream().limit(limit).toList();
        if (visibleEvents.isEmpty()) {
            return List.of();
        }

        Set<String> readNotificationIds = notificationReadRepository.findByUserIdAndNotificationIdIn(
                        userId,
                        visibleEvents.stream().map(NotificationEvent::notificationId).toList()
                ).stream()
                .map(NotificationReadEntity::getNotificationId)
                .collect(Collectors.toSet());

        return visibleEvents.stream()
                .map(event -> event.toDto(
                        readNotificationIds.contains(event.notificationId()) || event.isActor(userId),
                        userId
                ))
                .toList();
    }

    private NotificationSupportContext buildSupportContext(
            String libraryId,
            List<CommentEntity> comments,
            List<PostEntity> posts,
            List<TrashItemEntity> trashItems,
            List<UploadTaskEntity> uploadTasks,
            List<BowelEventEntity> bowelEvents
    ) {
        Set<String> userIds = new HashSet<>();
        comments.stream()
                .map(CommentEntity::getAuthorId)
                .forEach(userIds::add);
        comments.stream()
                .map(CommentEntity::getLastEditedByUserId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(userIds::add);
        comments.stream()
                .map(CommentEntity::getDeletedByUserId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(userIds::add);
        uploadTasks.stream()
                .map(UploadTaskEntity::getUploadedByUserId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(userIds::add);
        posts.stream()
                .map(PostEntity::getCreatorUserId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(userIds::add);
        trashItems.stream()
                .map(TrashItemEntity::getActorUserId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(userIds::add);
        bowelEvents.stream()
                .map(BowelEventEntity::getUserId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(userIds::add);
        Map<String, UserEntity> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        Set<String> postIds = new HashSet<>();
        comments.stream()
                .filter(comment -> comment.getTargetType() == CommentTargetType.SMALL_ALBUM && comment.getPostId() != null)
                .map(CommentEntity::getPostId)
                .forEach(postIds::add);
        trashItems.stream()
                .map(TrashItemEntity::getSourcePostId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(postIds::add);
        posts.stream()
                .map(PostEntity::getId)
                .forEach(postIds::add);
        Map<String, PostEntity> postsById = postIds.isEmpty()
                ? Map.of()
                : postRepository.findByLibraryIdAndIdIn(libraryId, postIds).stream()
                .collect(Collectors.toMap(PostEntity::getId, post -> post, (left, right) -> left, HashMap::new));

        Set<String> mediaIds = new HashSet<>();
        comments.stream()
                .filter(comment -> comment.getTargetType() == CommentTargetType.MEDIA && comment.getMediaId() != null)
                .map(CommentEntity::getMediaId)
                .forEach(mediaIds::add);
        trashItems.stream()
                .map(TrashItemEntity::getSourceMediaId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(mediaIds::add);
        posts.stream()
                .map(PostEntity::getCoverMediaId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(mediaIds::add);
        uploadTasks.stream()
                .map(UploadTaskEntity::getMediaId)
                .filter(value -> value != null && !value.isBlank())
                .forEach(mediaIds::add);
        Map<String, MediaEntity> mediaById = mediaIds.isEmpty()
                ? Map.of()
                : mediaRepository.findByLibraryIdAndIdIn(libraryId, mediaIds).stream()
                .collect(Collectors.toMap(MediaEntity::getId, media -> media, (left, right) -> left, HashMap::new));

        return new NotificationSupportContext(usersById, postsById, mediaById);
    }

    private List<NotificationEvent> toCommentEvents(
            CommentEntity comment,
            AuthenticatedUser currentUser,
            NotificationSupportContext context
    ) {
        List<NotificationEvent> events = new ArrayList<>();
        if (comment.getDeletedAt() == null) {
            events.add(toCommentCreatedEvent(comment, context));
        }

        if (!currentUser.userId().equals(comment.getAuthorId())) {
            return events;
        }

        String editorUserId = emptyToNull(comment.getLastEditedByUserId());
        if (editorUserId != null && !editorUserId.equals(comment.getAuthorId())) {
            events.add(toCommentEditedEvent(comment, editorUserId, context));
        }

        String deleterUserId = emptyToNull(comment.getDeletedByUserId());
        if (deleterUserId != null && !deleterUserId.equals(comment.getAuthorId())) {
            events.add(toCommentDeletedEvent(comment, deleterUserId, context));
        }

        return events;
    }

    private NotificationEvent toCommentCreatedEvent(CommentEntity comment, NotificationSupportContext context) {
        UserEntity author = context.usersById().get(comment.getAuthorId());
        ActorDescriptor actor = actorDescriptor(comment.getAuthorId(), author, null);
        CommentTargetDescriptor target = describeCommentTarget(comment, context);
        String title = target.smallAlbumTarget()
                ? actor.displayName() + "评论了小相册"
                : actor.displayName() + "评论了媒体";
        long createdAtMillis = toEpochMillis(comment.getCreatedAt());
        return new NotificationEvent(
                "comment:" + comment.getId(),
                "comment",
                "photos",
                "comment",
                title,
                abbreviate(comment.getContent(), 120),
                createdAtMillis,
                actor,
                "comment:" + comment.getId(),
                null,
                1,
                mediaItemsForTarget(comment.getMediaId(), comment.getPostId(), context),
                routeForTarget(comment.getPostId(), comment.getMediaId(), null),
                target.targetSummary(),
                target.targetType(),
                comment.getPostId(),
                comment.getMediaId(),
                null
        );
    }

    private NotificationEvent toCommentEditedEvent(
            CommentEntity comment,
            String editorUserId,
            NotificationSupportContext context
    ) {
        UserEntity editor = context.usersById().get(editorUserId);
        ActorDescriptor actor = actorDescriptor(editorUserId, editor, null);
        CommentTargetDescriptor target = describeCommentTarget(comment, context);
        String title = target.smallAlbumTarget()
                ? actor.displayName() + "编辑了你的小相册评论"
                : actor.displayName() + "编辑了你的媒体评论";
        long createdAtMillis = toEpochMillis(comment.getUpdatedAt());
        return new NotificationEvent(
                "comment-edit:" + comment.getId() + ":" + createdAtMillis,
                "comment_edit",
                "photos",
                "comment",
                title,
                abbreviate(comment.getContent(), 120),
                createdAtMillis,
                actor,
                "comment:" + comment.getId(),
                null,
                1,
                mediaItemsForTarget(comment.getMediaId(), comment.getPostId(), context),
                routeForTarget(comment.getPostId(), comment.getMediaId(), null),
                target.targetSummary(),
                target.targetType(),
                comment.getPostId(),
                comment.getMediaId(),
                null
        );
    }

    private NotificationEvent toCommentDeletedEvent(
            CommentEntity comment,
            String deleterUserId,
            NotificationSupportContext context
    ) {
        UserEntity deleter = context.usersById().get(deleterUserId);
        ActorDescriptor actor = actorDescriptor(deleterUserId, deleter, null);
        CommentTargetDescriptor target = describeCommentTarget(comment, context);
        String title = target.smallAlbumTarget()
                ? actor.displayName() + "删除了你的小相册评论"
                : actor.displayName() + "删除了你的媒体评论";
        long createdAtMillis = toEpochMillis(comment.getDeletedAt());
        String deletedContent = abbreviate(comment.getContent(), 80);
        String deletedBody = deletedContent != null && !deletedContent.isBlank()
                ? "被删除的评论：" + deletedContent
                : "你的评论被对方删除。";
        return new NotificationEvent(
                "comment-delete:" + comment.getId() + ":" + createdAtMillis,
                "comment_delete",
                "photos",
                "comment",
                title,
                deletedBody,
                createdAtMillis,
                actor,
                "comment:" + comment.getId(),
                null,
                1,
                mediaItemsForTarget(comment.getMediaId(), comment.getPostId(), context),
                routeForTarget(comment.getPostId(), comment.getMediaId(), null),
                target.targetSummary(),
                target.targetType(),
                comment.getPostId(),
                comment.getMediaId(),
                null
        );
    }

    private NotificationEvent toPostEvent(PostEntity post, AuthenticatedUser currentUser, NotificationSupportContext context) {
        long updatedAtMillis = toEpochMillis(post.getUpdatedAt());
        String albumName = safeTitle(post.getTitle());
        String title = "「" + albumName + "」有内容更新";
        String summary = abbreviate(post.getSummary(), 120);
        String body = summary == null || summary.isBlank()
                ? title + "。"
                : summary;
        String actorUserId = emptyToNull(post.getLastModifiedByUserId());
        if (actorUserId == null) {
            actorUserId = post.getCreatorUserId();
        }
        ActorDescriptor actor = actorDescriptor(actorUserId, context.usersById().get(actorUserId), null);
        return new NotificationEvent(
                "post:" + post.getId(),
                "content_update",
                "photos",
                "content_update",
                "小相册有内容更新",
                body,
                updatedAtMillis,
                actor,
                "post:" + post.getId(),
                null,
                1,
                mediaItemsForTarget(null, post.getId(), context),
                routeForTarget(post.getId(), null, null),
                safeTitle(post.getTitle()),
                "SMALL_ALBUM",
                post.getId(),
                null,
                null
        );
    }

    private NotificationEvent toTrashEvent(TrashItemEntity item, AuthenticatedUser currentUser, NotificationSupportContext context) {
        long createdAtMillis = toEpochMillis(item.getUpdatedAt());
        String itemName = safeTitle(item.getTitle());
        // P1-3 隔离修复 S7: 按 lifeCategory 区分 module, life trash 通知不进入照片通知中心
        boolean isLifeTrash = item.getLifeCategory() != null;
        String module = isLifeTrash ? "life" : "photos";
        String title = switch (item.getState()) {
            case IN_TRASH -> "「" + itemName + "」已移入回收站";
            case PENDING_CLEANUP -> "「" + itemName + "」等待彻底清理";
            case RESTORED -> "「" + itemName + "」已从回收站恢复";
            case PURGING -> "「" + itemName + "」正在彻底清理";
        };
        String body = switch (item.getState()) {
            case IN_TRASH -> itemName + " 已进入回收站。";
            case PENDING_CLEANUP -> itemName + " 正在等待永久清理。";
            case RESTORED -> itemName + " 已恢复到原位置。";
            case PURGING -> itemName + " 正在彻底清理。";
        };
        ActorDescriptor actor = actorDescriptor(item.getActorUserId(), context.usersById().get(item.getActorUserId()), null);
        return new NotificationEvent(
                "trash:" + item.getId() + ":" + createdAtMillis,
                "delete_restore",
                module,
                "delete",
                title,
                body,
                createdAtMillis,
                actor,
                "trash:" + item.getId(),
                null,
                1,
                mediaItemsForTarget(item.getSourceMediaId(), item.getSourcePostId(), context),
                routeForTarget(item.getSourcePostId(), item.getSourceMediaId(), item.getId()),
                abbreviate(item.getPreviewInfo(), 120),
                item.getItemType().name(),
                emptyToNull(item.getSourcePostId()),
                emptyToNull(item.getSourceMediaId()),
                item.getId()
        );
    }

    private List<NotificationEvent> toUploadEvents(
            List<UploadTaskEntity> uploadTasks,
            AuthenticatedUser currentUser,
            NotificationSupportContext context
    ) {
        Map<String, List<UploadTaskEntity>> tasksByOperation = uploadTasks.stream()
                // 过滤掉 life domain 的 upload task — 这些由 lifeConsoleMediaEvent 处理，
                // 避免同一操作生成两条通知（upload event + life_media event）
                .filter(task -> {
                    String mediaId = task.getMediaId();
                    if (mediaId == null || mediaId.isBlank()) return true;
                    MediaEntity media = context.mediaById().get(mediaId);
                    if (media != null && "life".equals(media.getDomain())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.groupingBy(task -> {
                    String operationId = emptyToNull(task.getOperationId());
                    return operationId == null ? task.getId() : operationId;
                }));
        return tasksByOperation.values().stream()
                .map(group -> toUploadOperationEvent(group, currentUser, context))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(NotificationEvent::createdAtMillis).reversed())
                .toList();
    }

    private NotificationEvent toUploadOperationEvent(
            List<UploadTaskEntity> tasks,
            AuthenticatedUser currentUser,
            NotificationSupportContext context
    ) {
        List<UploadTaskEntity> sorted = tasks.stream()
                .sorted(Comparator.comparingLong(this::uploadTaskSortMillis).reversed())
                .toList();
        UploadTaskEntity primary = sorted.get(0);
        if ("LIFE_CONSOLE".equalsIgnoreCase(primary.getOperationType())) {
            return null;
        }
        long createdAtMillis = sorted.stream().mapToLong(this::uploadTaskSortMillis).max().orElse(0L);
        String operationId = emptyToNull(primary.getOperationId());
        int successCount = (int) sorted.stream().filter(task -> task.getState() == UploadState.SUCCESS).count();
        int cancelledCount = (int) sorted.stream().filter(task -> task.getState() == UploadState.CANCELLED).count();
        int totalCount = primary.getOperationMediaCount() == null ? sorted.size() : Math.max(primary.getOperationMediaCount(), sorted.size());
        String title = uploadOperationTitle(primary, totalCount);
        String body = cancelledCount > 0
                ? successCount + " 项已导入，" + cancelledCount + " 项已取消。"
                : successCount + " 项媒体已导入共享空间。";
        ActorDescriptor actor = actorDescriptor(primary.getUploadedByUserId(), context.usersById().get(primary.getUploadedByUserId()), null);
        List<NotificationMediaItemDto> mediaItems = sorted.stream()
                .map(UploadTaskEntity::getMediaId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .map(context.mediaById()::get)
                .filter(media -> media != null)
                .map(this::toNotificationMediaItem)
                .toList();
        String firstMediaId = mediaItems.isEmpty() ? emptyToNull(primary.getMediaId()) : mediaItems.get(0).mediaId();
        return new NotificationEvent(
                "upload:" + (operationId == null ? primary.getId() : operationId) + ":" + createdAtMillis,
                "content_update",
                "photos",
                "content_update",
                title,
                body,
                createdAtMillis,
                actor,
                "upload:" + (operationId == null ? primary.getId() : operationId),
                operationId,
                totalCount,
                mediaItems,
                routeForTarget(null, firstMediaId, null),
                title,
                "UPLOAD",
                null,
                firstMediaId,
                null
        );
    }

    private void ensureReadRecord(String userId, String notificationId) {
        if (notificationReadRepository.findByUserIdAndNotificationId(userId, notificationId).isPresent()) {
            return;
        }
        notificationReadRepository.save(buildReadEntity(userId, notificationId, Instant.now()));
    }

    private NotificationReadEntity buildReadEntity(String userId, String notificationId, Instant readAt) {
        NotificationReadEntity entity = new NotificationReadEntity();
        entity.setId(IdGenerator.newId("notification_read"));
        entity.setUserId(userId);
        entity.setNotificationId(notificationId);
        entity.setReadAt(readAt);
        return entity;
    }

    private NotificationDto withReadState(NotificationDto notification, boolean isRead) {
        return new NotificationDto(
                notification.notificationId(),
                notification.type(),
                notification.module(),
                notification.category(),
                notification.title(),
                notification.body(),
                notification.createdAtMillis(),
                isRead,
                notification.actorUserId(),
                notification.actorDisplayName(),
                notification.actorAvatarUrl(),
                notification.actorIsCurrentUser(),
                notification.groupId(),
                notification.operationId(),
                notification.groupItemCount(),
                notification.mediaItems(),
                notification.targetRoute(),
                notification.targetSummary(),
                notification.targetType(),
                notification.smallAlbumId(),
                notification.mediaId(),
                notification.trashItemId()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private CommentTargetDescriptor describeCommentTarget(
            CommentEntity comment,
            NotificationSupportContext context
    ) {
        boolean smallAlbumTarget = comment.getTargetType() == CommentTargetType.SMALL_ALBUM;
        String targetSummary = smallAlbumTarget
                ? resolvePostSummary(context.postsById().get(comment.getPostId()))
                : resolveMediaSummary(context.mediaById().get(comment.getMediaId()), comment.getMediaId());
        return new CommentTargetDescriptor(targetSummary, comment.getTargetType().name(), smallAlbumTarget);
    }

    private String resolvePostSummary(PostEntity post) {
        return post == null ? "小相册" : safeTitle(post.getTitle());
    }

    private String resolveMediaSummary(MediaEntity media, String mediaId) {
        if (media == null) {
            return mediaId == null || mediaId.isBlank() ? "媒体" : "媒体文件";
        }
        String label = media.getMediaType() == MediaType.VIDEO ? "视频" : "照片";
        return label;
    }

    private String resolveUserName(UserEntity user) {
        return user == null || user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? "对方"
                : user.getDisplayName().trim();
    }

    private ActorDescriptor actorDescriptor(String actorUserId, UserEntity user, String fallbackUserId) {
        String resolvedUserId = emptyToNull(actorUserId);
        if (resolvedUserId == null) {
            resolvedUserId = emptyToNull(fallbackUserId);
        }
        return new ActorDescriptor(
                resolvedUserId,
                resolveUserName(user),
                user == null ? null : emptyToNull(user.getAvatarUrl())
        );
    }

    private List<NotificationMediaItemDto> mediaItemsForTarget(
            String mediaId,
            String postId,
            NotificationSupportContext context
    ) {
        String directMediaId = emptyToNull(mediaId);
        if (directMediaId != null) {
            MediaEntity media = context.mediaById().get(directMediaId);
            return media == null ? List.of() : List.of(toNotificationMediaItem(media));
        }
        PostEntity post = context.postsById().get(emptyToNull(postId));
        if (post != null && emptyToNull(post.getCoverMediaId()) != null) {
            MediaEntity media = context.mediaById().get(post.getCoverMediaId());
            return media == null ? List.of() : List.of(toNotificationMediaItem(media));
        }
        return List.of();
    }

    private NotificationMediaItemDto toNotificationMediaItem(MediaEntity media) {
        return new NotificationMediaItemDto(
                media.getId(),
                media.getMediaType() == null ? null : media.getMediaType().name(),
                media.getMimeType(),
                media.getPreviewUrl(),
                media.getPreviewUrl(),
                media.getCoverUrl(),
                media.getOriginalUrl(),
                media.getVideoUrl(),
                media.getDisplayTimeMillis(),
                media.getDurationMillis()
        );
    }

    private String routeForTarget(String postId, String mediaId, String trashItemId) {
        if (emptyToNull(trashItemId) != null) {
            return "photos:trash:" + trashItemId;
        }
        if (emptyToNull(mediaId) != null) {
            return "photos:media:" + mediaId;
        }
        if (emptyToNull(postId) != null) {
            return "photos:small-album:" + postId;
        }
        return "notifications:detail";
    }

    private long uploadTaskSortMillis(UploadTaskEntity task) {
        if (task.getCompletedAt() != null) {
            return task.getCompletedAt().toEpochMilli();
        }
        return toEpochMillis(task.getUpdatedAt());
    }

    private String uploadOperationTitle(UploadTaskEntity task, int totalCount) {
        String operationTitle = emptyToNull(task.getOperationTitle());
        if (operationTitle != null) {
            return operationTitle;
        }
        String operationType = emptyToNull(task.getOperationType());
        if ("CREATE_POST".equalsIgnoreCase(operationType)) {
            return "导入并创建小相册";
        }
        if ("ADD_TO_EXISTING_POST".equalsIgnoreCase(operationType)) {
            return "导入到小相册";
        }
        return totalCount > 1 ? "导入到照片流" : safeTitle(task.getFileName());
    }

    private NotificationEvent toBowelEventNotification(
            BowelEventEntity event,
            AuthenticatedUser currentUser,
            NotificationSupportContext context
    ) {
        UserEntity actor = context.usersById().get(event.getUserId());
        ActorDescriptor actorDesc = actorDescriptor(event.getUserId(), actor, null);
        long createdAtMillis = toEpochMillis(event.getUpdatedAt());
        String timeLabel = formatBowelEventTime(event.getOccurredAtMillis());
        return new NotificationEvent(
                "bowel:" + event.getId(),
                "content_update",
                "life",
                "trace",
                actorDesc.displayName() + "记录了今日痕迹",
                timeLabel + " 记录了一次💩",
                createdAtMillis,
                actorDesc,
                "bowel:" + event.getId(),
                null,
                1,
                List.of(),
                "life:bowel",
                "今日痕迹",
                "LIFE_BOWEL",
                null,
                null,
                null
        );
    }

    private String formatBowelEventTime(Long occurredAtMillis) {
        if (occurredAtMillis == null || occurredAtMillis <= 0) {
            return "刚刚";
        }
        java.time.Instant instant = java.time.Instant.ofEpochMilli(occurredAtMillis);
        java.time.LocalDateTime localDateTime = java.time.LocalDateTime.ofInstant(
                instant, java.time.ZoneId.of("Asia/Shanghai"));
        return localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    /**
     * Generates a notification event for a person/meal media upload in a
     * life-console album.  This ensures the SyncVersionTracker polling
     * fallback can deliver a notification when FCM is unreachable.
     */
    private NotificationEvent toLifeConsoleMediaEvent(
            PostEntity post,
            String systemKey,
            AuthenticatedUser currentUser,
            NotificationSupportContext context
    ) {
        String actorUserId = post.getLastModifiedByUserId();
        if (actorUserId == null) {
            actorUserId = post.getCreatorUserId();
        }
        UserEntity actor = actorUserId != null
                ? userRepository.findById(actorUserId).orElse(null)
                : null;
        ActorDescriptor actorDesc = actorDescriptor(actorUserId, actor, null);
        long createdAtMillis = toEpochMillis(post.getUpdatedAt());

        String categoryLabel = "person".equals(systemKey) ? "人物痕迹" : "吃饭痕迹";
        String actionLabel = "person".equals(systemKey) ? "人物照片" : "吃饭照片";

        // 查询 post 最新的 mediaId，用于回退通知精准跳转
        // 之前 BUG: 不包含 mediaId，导致回退通知无法精准跳转，总是跳到第一张
        String latestMediaId = findLatestMediaIdForPost(post.getLibraryId(), post.getId());

        return new NotificationEvent(
                "life_media:" + post.getId(),
                "content_update",
                "life",
                "trace",
                actorDesc.displayName() + "更新了" + categoryLabel,
                "对方上传了" + actionLabel,
                createdAtMillis,
                actorDesc,
                "life_media:" + post.getId(),
                null,
                1,
                List.of(),
                "life:trace",
                "今日痕迹",
                "LIFE_TRACE",
                null,
                latestMediaId,
                null
        );
    }

    /**
     * 查询 post 最新的 mediaId（按 sortOrder 降序取第一个）。
     * 用于回退通知精准跳转到对应媒体的查看态。
     */
    private String findLatestMediaIdForPost(String libraryId, String postId) {
        if (postId == null || postId.isBlank()) return null;
        List<PostMediaEntity> relations = postMediaRepository.findByLibraryIdAndPostIdOrderBySortOrderAsc(libraryId, postId);
        if (relations.isEmpty()) return null;
        // sortOrder 升序排列，最新的在最后
        return relations.get(relations.size() - 1).getMediaId();
    }

    private long toEpochMillis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    private String safeTitle(String value) {
        if (value == null || value.isBlank()) {
            return "Untitled content";
        }
        return value.trim();
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record NotificationSupportContext(
            Map<String, UserEntity> usersById,
            Map<String, PostEntity> postsById,
            Map<String, MediaEntity> mediaById
    ) {
    }

    private record ActorDescriptor(
            String userId,
            String displayName,
            String avatarUrl
    ) {
    }

    private record CommentTargetDescriptor(
            String targetSummary,
            String targetType,
            boolean smallAlbumTarget
    ) {
    }

    private record NotificationEvent(
            String notificationId,
            String type,
            String module,
            String category,
            String title,
            String body,
            long createdAtMillis,
            ActorDescriptor actor,
            String groupId,
            String operationId,
            Integer groupItemCount,
            List<NotificationMediaItemDto> mediaItems,
            String targetRoute,
            String targetSummary,
            String targetType,
            String smallAlbumId,
            String mediaId,
            String trashItemId
    ) {
        private boolean isActor(String userId) {
            return actor != null && actor.userId() != null && actor.userId().equals(userId);
        }

        private NotificationDto toDto(boolean isRead, String currentUserId) {
            boolean actorIsCurrentUser = isActor(currentUserId);
            return new NotificationDto(
                    notificationId,
                    type,
                    module,
                    category,
                    title,
                    body,
                    createdAtMillis,
                    isRead,
                    actor == null ? null : actor.userId(),
                    actor == null ? null : actor.displayName(),
                    actor == null ? null : actor.avatarUrl(),
                    actorIsCurrentUser,
                    groupId,
                    operationId,
                    groupItemCount,
                    mediaItems == null ? List.of() : mediaItems,
                    targetRoute,
                    targetSummary,
                    targetType,
                    smallAlbumId,
                    mediaId,
                    trashItemId
            );
        }
    }
}
