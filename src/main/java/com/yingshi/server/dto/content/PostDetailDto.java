package com.yingshi.server.dto.content;

import java.util.List;

public record PostDetailDto(
        String postId,
        String title,
        String summary,
        String contributorLabel,
        Long displayTimeMillis,
        Long eventStartedAtMillis,
        Long eventEndedAtMillis,
        String displayTimeSource,
        List<String> albumIds,
        String coverMediaId,
        long mediaCount,
        List<PostMediaDto> mediaItems
) {
}
