package com.yingshi.server.service.storage;

public record ObjectMetadata(
        String objectKey,
        String contentType,
        Long sizeBytes,
        String checksum,
        Long lastModifiedMillis
) {
}
