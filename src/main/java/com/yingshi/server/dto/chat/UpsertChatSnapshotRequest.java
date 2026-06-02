package com.yingshi.server.dto.chat;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpsertChatSnapshotRequest(
        @NotNull(message = "payload is required.")
        Map<String, Object> payload
) {
}
