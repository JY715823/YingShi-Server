package com.yingshi.server.dto.chat;

public record ChatImportedSyncRequest(
        long lastSyncVersionMillis,
        ChatImportedClientChangesDto changes
) {
}
