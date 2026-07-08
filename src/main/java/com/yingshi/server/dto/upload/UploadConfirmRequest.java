package com.yingshi.server.dto.upload;

import jakarta.validation.constraints.Size;

public record UploadConfirmRequest(
        @Size(max = 256, message = "etag must be at most 256 characters.")
        String etag,
        @Size(max = 2048, message = "objectKey must be at most 2048 characters.")
        String objectKey
) {
}
