package com.yingshi.server.dto.trash;

import java.util.List;

public record TrashPageResponse(
        List<TrashItemDto> items,
        int page,
        int size,
        long totalElements,
        boolean hasMore
) {
}
