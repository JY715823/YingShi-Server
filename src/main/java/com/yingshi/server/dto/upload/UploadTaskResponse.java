package com.yingshi.server.dto.upload;

import com.yingshi.server.dto.content.MediaDto;

public record UploadTaskResponse(
        String uploadId,
        String fileName,
        String mediaType,
        String objectKey,
        String mediaId,
        String state,
        int progressPercent,
        String errorMessage,
        String operationId,
        String operationType,
        String operationTitle,
        Integer operationMediaCount,
        String sourceItemId,
        Long createdAtMillis,
        Long updatedAtMillis,
        Long completedAtMillis,
        MediaDto media
) {
}
