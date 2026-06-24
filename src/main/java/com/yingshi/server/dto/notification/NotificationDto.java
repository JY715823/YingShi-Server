package com.yingshi.server.dto.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record NotificationDto(
        String notificationId,
        String type,
        String module,
        String category,
        String title,
        String body,
        long createdAtMillis,
        boolean isRead,
        String actorUserId,
        String actorDisplayName,
        String actorAvatarUrl,
        boolean actorIsCurrentUser,
        String groupId,
        String operationId,
        Integer groupItemCount,
        java.util.List<NotificationMediaItemDto> mediaItems,
        String targetRoute,
        String targetSummary,
        String targetType,
        String smallAlbumId,
        String mediaId,
        String trashItemId
) {
    public NotificationDto(
            String notificationId,
            String type,
            String title,
            String body,
            long createdAtMillis,
            boolean isRead,
            String targetSummary,
            String targetType,
            String smallAlbumId,
            String mediaId,
            String trashItemId
    ) {
        this(
                notificationId,
                type,
                null,
                null,
                title,
                body,
                createdAtMillis,
                isRead,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                java.util.List.of(),
                null,
                targetSummary,
                targetType,
                smallAlbumId,
                mediaId,
                trashItemId
        );
    }

    @JsonIgnore
    public String postId() {
        return smallAlbumId;
    }
}
