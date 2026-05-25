package com.yingshi.server.dto.notification;

public record NotificationMarkAllReadResponse(
        boolean success,
        int affectedCount
) {
}
