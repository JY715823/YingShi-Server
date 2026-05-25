package com.yingshi.server.dto.upload;

public record UploadTaskResponse(
        String uploadId,
        String fileName,
        String mediaType,
        String objectKey,
        String state,
        int progressPercent,
        String errorMessage
) {
}
