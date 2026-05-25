package com.yingshi.server.dto.notification;

public record NotificationDto(
        String notificationId,
        String type,
        String title,
        String body,
        long createdAtMillis,
        boolean isRead,
        String targetSummary,
        String targetType,
        String postId,
        String mediaId,
        String trashItemId
) {
}
