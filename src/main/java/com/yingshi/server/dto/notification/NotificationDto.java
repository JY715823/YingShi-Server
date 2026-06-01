package com.yingshi.server.dto.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record NotificationDto(
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
    @JsonIgnore
    public String postId() {
        return smallAlbumId;
    }
}
