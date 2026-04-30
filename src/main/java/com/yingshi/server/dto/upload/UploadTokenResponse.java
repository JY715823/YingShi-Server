package com.yingshi.server.dto.upload;

public record UploadTokenResponse(
        String uploadId,
        String provider,
        String uploadUrl,
        Long expireAtMillis,
        String state
) {
}
