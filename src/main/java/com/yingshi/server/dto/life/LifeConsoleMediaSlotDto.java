package com.yingshi.server.dto.life;

import com.yingshi.server.dto.content.MediaDto;

import java.util.List;

public record LifeConsoleMediaSlotDto(
        String category,
        String ownerUserId,
        boolean editable,
        List<MediaDto> mediaItems
) {
}
