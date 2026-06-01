package com.yingshi.server.service.notification;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
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
import com.yingshi.server.dto.notification.NotificationDto;
import com.yingshi.server.dto.notification.NotificationMarkAllReadResponse;
import com.yingshi.server.repository.CommentRepository;
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
import java.util.Locale;
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

    public NotificationService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            TrashItemRepository trashItemRepository,
            UploadTaskRepository uploadTaskRepository,
            NotificationReadRepository notificationReadRepository,
            UserRepository userRepository,
            MediaRepository mediaRepository
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.trashItemRepository = trashItemRepository;
        this.uploadTaskRepository = uploadTaskRepository;
        this.notificationReadRepository = notificationReadRepository;
        this.userRepository = userRepository;
        this.mediaRepository = mediaRepository;
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

        NotificationSupportContext context = buildSupportContext(libraryId, comments, posts, trashItems, uploadTasks);

        List<NotificationEvent> events = new ArrayList<>();
        comments.forEach(comment -> events.addAll(toCommentEvents(comment, currentUser, context)));
        posts.forEach(post -> events.add(toPostEvent(post)));
        trashItems.forEach(item -> events.add(toTrashEvent(item)));
        uploadTasks.forEach(task -> events.add(toUploadEvent(task, context)));

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
                .map(event -> event.toDto(readNotificationIds.contains(event.notificationId())))
                .toList();
    }

    private NotificationSupportContext buildSupportContext(
            String libraryId,
            List<CommentEntity> comments,
            List<PostEntity> posts,
            List<TrashItemEntity> trashItems,
            List<UploadTaskEntity> uploadTasks
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
        if (comment.getDeletedAt() == null && !currentUser.userId().equals(comment.getAuthorId())) {
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
        String actorName = resolveUserName(author);
        CommentTargetDescriptor target = describeCommentTarget(comment, context);
        String title = target.smallAlbumTarget()
                ? actorName + " commented on a small album"
                : actorName + " commented on a media item";
        long createdAtMillis = toEpochMillis(comment.getCreatedAt());
        return new NotificationEvent(
                "comment:" + comment.getId(),
                "comment",
                title,
                abbreviate(comment.getContent(), 120),
                createdAtMillis,
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
        String actorName = resolveUserName(editor);
        CommentTargetDescriptor target = describeCommentTarget(comment, context);
        String title = target.smallAlbumTarget()
                ? actorName + " edited your small album comment"
                : actorName + " edited your media comment";
        long createdAtMillis = toEpochMillis(comment.getUpdatedAt());
        return new NotificationEvent(
                "comment-edit:" + comment.getId() + ":" + createdAtMillis,
                "comment_edit",
                title,
                abbreviate(comment.getContent(), 120),
                createdAtMillis,
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
        String actorName = resolveUserName(deleter);
        CommentTargetDescriptor target = describeCommentTarget(comment, context);
        String title = target.smallAlbumTarget()
                ? actorName + " deleted your small album comment"
                : actorName + " deleted your media comment";
        long createdAtMillis = toEpochMillis(comment.getDeletedAt());
        return new NotificationEvent(
                "comment-delete:" + comment.getId() + ":" + createdAtMillis,
                "comment_delete",
                title,
                "Your comment was deleted.",
                createdAtMillis,
                target.targetSummary(),
                target.targetType(),
                comment.getPostId(),
                comment.getMediaId(),
                null
        );
    }

    private NotificationEvent toPostEvent(PostEntity post) {
        long updatedAtMillis = toEpochMillis(post.getUpdatedAt());
        String summary = abbreviate(post.getSummary(), 120);
        String body = summary == null || summary.isBlank()
                ? "\"" + safeTitle(post.getTitle()) + "\" was updated."
                : summary;
        return new NotificationEvent(
                "post:" + post.getId() + ":" + updatedAtMillis,
                "content_update",
                "Small album updated",
                body,
                updatedAtMillis,
                safeTitle(post.getTitle()),
                "SMALL_ALBUM",
                post.getId(),
                null,
                null
        );
    }

    private NotificationEvent toTrashEvent(TrashItemEntity item) {
        long createdAtMillis = toEpochMillis(item.getUpdatedAt());
        String title = switch (item.getState()) {
            case IN_TRASH -> "Content moved to trash";
            case PENDING_CLEANUP -> "Content moved out of trash";
            case RESTORED -> "Content restored from trash";
        };
        String body = switch (item.getState()) {
            case IN_TRASH -> safeTitle(item.getTitle()) + " was moved to trash.";
            case PENDING_CLEANUP -> safeTitle(item.getTitle()) + " is waiting for permanent cleanup.";
            case RESTORED -> safeTitle(item.getTitle()) + " was restored to its original location.";
        };
        return new NotificationEvent(
                "trash:" + item.getId() + ":" + createdAtMillis,
                "delete_restore",
                title,
                body,
                createdAtMillis,
                abbreviate(item.getPreviewInfo(), 120),
                item.getItemType().name(),
                emptyToNull(item.getSourcePostId()),
                emptyToNull(item.getSourceMediaId()),
                item.getId()
        );
    }

    private NotificationEvent toUploadEvent(UploadTaskEntity task, NotificationSupportContext context) {
        long createdAtMillis = task.getCompletedAt() != null
                ? task.getCompletedAt().toEpochMilli()
                : toEpochMillis(task.getUpdatedAt());
        String mediaSummary = task.getMediaId() == null
                ? safeTitle(task.getFileName())
                : resolveMediaSummary(context.mediaById().get(task.getMediaId()), task.getMediaId());
        String title = task.getState() == UploadState.SUCCESS ? "Upload completed" : "Upload cancelled";
        String body = task.getState() == UploadState.SUCCESS
                ? safeTitle(task.getFileName()) + " was imported into the shared library."
                : safeTitle(task.getFileName()) + " was cancelled before completion.";
        return new NotificationEvent(
                "upload:" + task.getId() + ":" + createdAtMillis,
                "system",
                title,
                body,
                createdAtMillis,
                mediaSummary,
                "UPLOAD",
                null,
                emptyToNull(task.getMediaId()),
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
                notification.title(),
                notification.body(),
                notification.createdAtMillis(),
                isRead,
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

    private record CommentTargetDescriptor(
            String targetSummary,
            String targetType,
            boolean smallAlbumTarget
    ) {
    }

    private record NotificationEvent(
            String notificationId,
            String type,
            String title,
            String body,
            long createdAtMillis,
            String targetSummary,
            String targetType,
            String smallAlbumId,
            String mediaId,
            String trashItemId
    ) {
        private NotificationDto toDto(boolean isRead) {
            return new NotificationDto(
                    notificationId,
                    type,
                    title,
                    body,
                    createdAtMillis,
                    isRead,
                    targetSummary,
                    targetType,
                    smallAlbumId,
                    mediaId,
                    trashItemId
            );
        }
    }
}
