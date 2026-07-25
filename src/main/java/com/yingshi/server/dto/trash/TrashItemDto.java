package com.yingshi.server.dto.trash;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record TrashItemDto(
        String trashItemId,
        String itemType,
        String state,
        String actorUserId,
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
        String sourceMediaMimeType,
        // P1-2 改造: life 回收站分类 (PERSON/MEAL/null); null 表示照片回收站
        String lifeCategory
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
