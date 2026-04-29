package com.yingshi.server.dto.content;

import java.util.List;

public record PostSummaryDto(
        String postId,
        String title,
        String summary,
        String contributorLabel,
        Long displayTimeMillis,
        List<String> albumIds,
        String coverMediaId,
        long mediaCount
) {
}
