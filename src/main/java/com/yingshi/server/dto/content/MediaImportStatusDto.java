package com.yingshi.server.dto.content;

import java.util.List;

public record MediaImportStatusDto(
        String sourceFingerprint,
        String mediaId,
        List<String> smallAlbumIds
) {
}
