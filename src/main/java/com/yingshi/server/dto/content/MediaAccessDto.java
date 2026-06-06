package com.yingshi.server.dto.content;

public record MediaAccessDto(
        String variant,
        String url,
        String signedUrl,
        Long expiresAtMillis,
        String cacheKey,
        String revision
) {
}
