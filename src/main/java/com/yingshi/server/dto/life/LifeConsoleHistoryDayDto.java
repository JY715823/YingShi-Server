package com.yingshi.server.dto.life;

import com.yingshi.server.dto.content.MediaDto;

import java.util.List;

public record LifeConsoleHistoryDayDto(
        String date,
        String displayLabel,
        List<MediaDto> selfMedia,
        List<MediaDto> partnerMedia
) {
}
