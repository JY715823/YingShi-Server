package com.yingshi.server.dto.trash;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record TrashItemDto(
        String trashItemId,
        String itemType,
        String state,
        String sourceSmallAlbumId,
        String sourceMediaId,
        String commentTargetMediaId,
        String title,
        String previewInfo,
        Long deletedAtMillis,
        List<String> relatedSmallAlbumIds,
        List<String> relatedMediaIds,
        String sourceMediaType,
        Integer sourceMediaWidth,
        Integer sourceMediaHeight,
        Double sourceMediaAspectRatio,
        Long sourceMediaDurationMillis,
        String sourceMediaMimeType
) {
    @JsonIgnore
    public String sourcePostId() {
        return sourceSmallAlbumId;
    }

    @JsonIgnore
    public List<String> relatedPostIds() {
        return relatedSmallAlbumIds;
    }
}
