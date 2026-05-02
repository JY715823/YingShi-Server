package com.yingshi.server.service.content;

import org.springframework.core.io.Resource;

public record MediaFilePayload(
        Resource resource,
        String mimeType,
        Long contentLength,
        Long lastModifiedMillis
) {
}
