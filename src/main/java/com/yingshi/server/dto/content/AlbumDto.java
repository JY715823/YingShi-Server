package com.yingshi.server.dto.content;

public record AlbumDto(
        String albumId,
        String title,
        String subtitle,
        String coverMediaId,
        long postCount
) {
}
