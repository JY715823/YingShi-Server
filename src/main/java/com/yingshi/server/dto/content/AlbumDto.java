package com.yingshi.server.dto.content;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AlbumDto(
        String albumId,
        String title,
        String subtitle,
        String coverMediaId,
        long smallAlbumCount
) {
    @JsonIgnore
    public long postCount() {
        return smallAlbumCount;
    }
}
