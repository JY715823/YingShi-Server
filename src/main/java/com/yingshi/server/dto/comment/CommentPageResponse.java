package com.yingshi.server.dto.comment;

import java.util.List;

public record CommentPageResponse(
        List<CommentDto> comments,
        int page,
        int size,
        long totalElements,
        boolean hasMore
) {
}
