package com.yingshi.server.dto.upload;

public record UploadConfirmRequest(
        String etag,
        String objectKey
) {
}
