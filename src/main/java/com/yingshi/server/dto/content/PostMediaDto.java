package com.yingshi.server.dto.content;

public record PostMediaDto(
        int sortOrder,
        boolean isCover,
        MediaDto media
) {
}
