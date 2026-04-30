package com.yingshi.server.dto.upload;

import com.yingshi.server.dto.content.MediaDto;

public record UploadCompleteResponse(
        String uploadId,
        String state,
        MediaDto media
) {
}
