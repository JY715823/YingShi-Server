package com.yingshi.server.dto.content;

import java.util.List;

public record MediaFeedPage(
        List<MediaDto> items,
        String nextCursor,
        boolean hasMore,
        int pageSize
) {
}
