package com.yingshi.server.dto.content;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record PostDetailDto(
        String smallAlbumId,
        String title,
        String summary,
        String contributorLabel,
        String creatorUserId,
        List<String> participantUserIds,
        Long displayTimeMillis,
        Long eventStartedAtMillis,
        Long eventEndedAtMillis,
        String displayTimeSource,
        String albumId,
        String systemKey,
        String coverMediaId,
        long mediaCount,
        List<PostMediaDto> mediaItems
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
