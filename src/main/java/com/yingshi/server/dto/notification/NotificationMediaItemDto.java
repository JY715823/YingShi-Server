package com.yingshi.server.dto.notification;

public record NotificationMediaItemDto(
        String mediaId,
        String mediaType,
        String mimeType,
        String previewUrl,
        String thumbnailUrl,
        String coverUrl,
        String mediaUrl,
        String videoUrl,
        Long displayTimeMillis,
        Long durationMillis
) {
}
