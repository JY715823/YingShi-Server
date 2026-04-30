package com.yingshi.server.dto.trash;

import java.util.List;

public record TrashItemDto(
        String trashItemId,
        String itemType,
        String state,
        String sourcePostId,
        String sourceMediaId,
        String title,
        String previewInfo,
        Long deletedAtMillis,
        List<String> relatedPostIds,
        List<String> relatedMediaIds
) {
}
