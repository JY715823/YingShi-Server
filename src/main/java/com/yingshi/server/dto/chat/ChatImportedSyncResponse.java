package com.yingshi.server.dto.chat;

public record ChatImportedSyncResponse(
        long versionMillis,
        ChatImportedChangesDto changes
) {
}
