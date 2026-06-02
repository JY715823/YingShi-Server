package com.yingshi.server.dto.chat;

import java.util.Map;

public record ChatSnapshotDto(
        long versionMillis,
        Map<String, Object> payload
) {
}
