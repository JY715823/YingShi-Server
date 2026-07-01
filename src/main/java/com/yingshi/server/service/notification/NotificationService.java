package com.yingshi.server.service.notification;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.BowelEventEntity;
import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import com.yingshi.server.domain.LedgerSnapshotEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.NotificationReadEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.TrashItemEntity;
import com.yingshi.server.domain.TrashItemState;
import com.yingshi.server.domain.UploadState;
import com.yingshi.server.domain.UploadTaskEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.notification.NotificationDto;
import com.yingshi.server.dto.notification.NotificationMarkAllReadResponse;
import com.yingshi.server.dto.notification.NotificationMediaItemDto;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.BowelEventRepository;
import com.yingshi.server.repository.CommentRepository;
import com.yingshi.server.repository.LedgerSnapshotRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.NotificationReadRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.TrashItemRepository;
import com.yingshi.server.repository.UploadTaskRepository;
import com.yingshi.server.repository.UserRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final TrashItemRepository trashItemRepository;
    private final UploadTaskRepository uploadTaskRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final BowelEventRepository bowelEventRepository;
    private final LedgerSnapshotRepository ledgerSnapshotRepository;
    private final AlbumRepository albumRepository;

    public NotificationService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            TrashItemRepository trashItemRepository,
            UploadTaskRepository uploadTaskRepository,
            NotificationReadRepository notificationReadRepository,
            UserRepository userRepository,
            MediaRepository mediaRepository,
            BowelEventRepository bowelEventRepository,
            LedgerSnapshotRepository ledgerSnapshotRepository,
            AlbumRepository albumRepository
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.trashItemRepository = trashItemRepository;
        this.uploadTaskRepository = uploadTaskRepository;
        this.notificationReadRepository = notificationReadRepository;
        this.userRepository = userRepository;
        this.mediaRepository = mediaRepository;
        this.bowelEventRepository = bowelEventRepository;
        this.ledgerSnapshotRepository = ledgerSnapshotRepository;
        this.albumRepository = albumRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listNotifications(AuthenticatedUser currentUser, Integer limit) {
        return materializeNotifications(
                collectNotificationEvents(currentUser),
                currentUser.userId(),
                normalizeLimit(limit)
        );
    }

    @Transactional(readOnly = true)
    public NotificationDto getNotification(String notificationId, AuthenticatedUser currentUser) {
        return materializeNotifications(
                collectNotificationEvents(currentUser),
                currentUser.userId(),
                null
        ).stream()
                .filter(notification -> notification.notificationId().equals(notificationId))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.NOT_FOUND,
                        "Notification was not found."
                ));
    }

    @Transactional
    public NotificationDto markRead(String notificationId, AuthenticatedUser currentUser) {
        NotificationDto notification = getNotification(notificationId, currentUser);
        ensureReadRecord(currentUser.userId(), notificationId);
        return withReadState(notification, true);
    }

    @Transactional
    public NotificationMarkAllReadResponse markAllRead(AuthenticatedUser currentUser) {
        List<NotificationEvent> events = collectNotificationEvents(currentUser);
        if (events.isEmpty()) {
            return new NotificationMarkAllReadResponse(true, 0);
        }

        List<String> notificationIds = events.stream()
                .map(NotificationEvent::notificationId)
                .toList();
        Set<String> existingReadIds = notificationReadRepository
                .findByUserIdAndNotificationIdIn(currentUser.userId(), notificationIds)
                .stream()
                .map(NotificationReadEntity::getNotificationId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        List<NotificationReadEntity> newReads = notificationIds.stream()
                .filter(notificationId -> !existingReadIds.contains(notificationId))
                .map(notificationId -> buildReadEntity(currentUser.userId(), notificationId, now))
                .toList();
        if (!newReads.isEmpty()) {
            notificationReadRepository.saveAll(newReads);
        }
        return new NotificationMarkAllReadResponse(true, newReads.size());
    }

    private List<NotificationEvent> collectNotificationEvents(AuthenticatedUser currentUser) {
        String libraryId = currentUser.libraryId();
        List<CommentEntity> comments = commentRepository.findByLibraryIdOrderByCreatedAtDesc(libraryId);
        List<PostEntity> posts = postRepository.findByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(libraryId);
        List<TrashItemEntity> trashItems = trashItemRepository.findByLibraryIdOrderByUpdatedAtDesc(libraryId);
        List<UploadTaskEntity> uploadTasks = uploadTaskRepository.findByLibraryIdOrderByUpdatedAtDesc(libraryId).stream()
                .filter(task -> task.getState() == UploadState.SUCCESS || task.getState() == UploadState.CANCELLED)
                .toList();
        List<BowelEventEntity> bowelEvents = bowelEventRepository.findTop50ByLibraryIdOrderByOccurredAtMillisDesc(libraryId);
        LedgerSnapshotEntity ledgerSnapshot = ledgerSnapshotRepository.findByLibraryId(libraryId).orElse(null);

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
        if (ledgerSnapshot != null && !currentUser.userId().equals(ledgerSnapshot.getLastModifiedByUserId())) {
            events.add(toLedgerNotification(ledgerSnapshot, currentUser));
        }

        return events.stream()
                .sorted(Comparator.comparingLong(NotificationEvent::createdAtMillis).reversed()
                        .thenComparing(NotificationEvent::notificationId))
                .toList();
    }

    private List<NotificationDto> materializeNotifications(
            List<NotificationEvent> events,
            String userId,
            Integer limit
    ) {
        List<NotificationEvent> visibleEvents = limit == null
                ? events
                : events.stream().limit(limit).toList();
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
        return new NotificationEvent(
                "comment-delete:" + comment.getId() + ":" + createdAtMillis,
                "comment_delete",
                "photos",
                "comment",
                title,
                "你的评论被对方删除。",
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
        String summary = abbreviate(post.getSummary(), 120);
        String body = summary == null || summary.isBlank()
                ? "「" + safeTitle(post.getTitle()) + "」有内容更新。"
                : summary;
        String actorUserId = emptyToNull(post.getLastModifiedByUserId());
        if (actorUserId == null) {
            actorUserId = post.getCreatorUserId();
        }
        ActorDescriptor actor = actorDescriptor(actorUserId, context.usersById().get(actorUserId), null);
        return new NotificationEvent(
                "post:" + post.getId() + ":" + updatedAtMillis,
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
        String title = switch (item.getState()) {
            case IN_TRASH -> "内容已移入回收站";
            case PENDING_CLEANUP -> "内容等待彻底清理";
            case RESTORED -> "内容已从回收站恢复";
        };
        String body = switch (item.getState()) {
            case IN_TRASH -> safeTitle(item.getTitle()) + " 已进入回收站。";
            case PENDING_CLEANUP -> safeTitle(item.getTitle()) + " 正在等待永久清理。";
            case RESTORED -> safeTitle(item.getTitle()) + " 已恢复到原位置。";
        };
        ActorDescriptor actor = actorDescriptor(item.getActorUserId(), context.usersById().get(item.getActorUserId()), null);
        return new NotificationEvent(
                "trash:" + item.getId() + ":" + createdAtMillis,
                "delete_restore",
                "photos",
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
        return post == null ? "Small album" : safeTitle(post.getTitle());
    }

    private String resolveMediaSummary(MediaEntity media, String mediaId) {
        if (media == null) {
            return mediaId == null || mediaId.isBlank() ? "Media" : "Media " + mediaId;
        }
        String label = media.getMediaType() == MediaType.VIDEO ? "Video" : "Photo";
        return label + " " + media.getId();
    }

    private String resolveUserName(UserEntity user) {
        return user == null || user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? "Another member"
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
                timeLabel + " 记录了一次排便",
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

    private NotificationEvent toLedgerNotification(
            LedgerSnapshotEntity snapshot,
            AuthenticatedUser currentUser
    ) {
        String modifierUserId = snapshot.getLastModifiedByUserId();
        UserEntity modifier = modifierUserId != null ? userRepository.findById(modifierUserId).orElse(null) : null;
        ActorDescriptor actor = actorDescriptor(modifierUserId, modifier, null);
        long updatedAtMillis = toEpochMillis(snapshot.getUpdatedAt());
        return new NotificationEvent(
                "ledger:" + snapshot.getLibraryId() + ":" + updatedAtMillis,
                "ledger_update",
                "life",
                "ledger",
                actor.displayName() + "更新了记账数据",
                "记账本有新的收支变动，点开可查看。",
                updatedAtMillis,
                actor,
                null,
                null,
                1,
                List.of(),
                "life:ledger",
                "记账",
                "LIFE_LEDGER",
                null,
                null,
                null
        );
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
