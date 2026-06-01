package com.yingshi.server.dto.content;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record PostSummaryDto(
        String smallAlbumId,
        String title,
        String summary,
        String contributorLabel,
        Long displayTimeMillis,
        Long eventStartedAtMillis,
        Long eventEndedAtMillis,
        String displayTimeSource,
        String albumId,
        String coverMediaId,
        long mediaCount
) {
    @JsonIgnore
    public String postId() {
        return smallAlbumId;
    }

    @JsonIgnore
    public List<String> albumIds() {
        return List.of(albumId);
    }
}
