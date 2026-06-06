package com.yingshi.server.dto.upload;

import java.util.Map;

public record UploadTokenResponse(
        String uploadId,
        String provider,
        String uploadUrl,
        Long expireAtMillis,
        String state,
        String uploadMethod,
        String objectKey,
        Map<String, String> headers,
        String confirmUrl
) {
}
