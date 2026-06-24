package com.yingshi.server.dto.sync;

public record SyncVersionsResponse(
        long photoFeedVersion,
        long albumsVersion,
        long trashVersion,
        long notificationVersion,
        long lifeConsoleVersion,
        long serverTimeMillis
) {
}
