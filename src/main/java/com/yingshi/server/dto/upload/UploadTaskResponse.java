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
        MediaDto media
) {
}
