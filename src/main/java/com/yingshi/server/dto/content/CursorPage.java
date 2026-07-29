package com.yingshi.server.dto.content;

import java.util.List;

/**
 * R2-E-3/4/5: Generic cursor-paginated page response.
 * Mirrors MediaFeedPage for list endpoints (albums, small albums).
 */
public record CursorPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore,
        int pageSize
) {
}
