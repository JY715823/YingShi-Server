package com.yingshi.server.service.storage;

import org.springframework.core.io.Resource;

public record StoredObject(
        String objectKey,
        Resource resource,
        ObjectMetadata metadata
) {
}
