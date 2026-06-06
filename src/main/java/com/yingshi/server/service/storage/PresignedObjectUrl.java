package com.yingshi.server.service.storage;

import java.util.Map;

public record PresignedObjectUrl(
        String url,
        Long expiresAtMillis,
        Map<String, String> headers
) {
}
