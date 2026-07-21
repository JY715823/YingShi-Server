package com.yingshi.server.dto.life;

import com.yingshi.server.dto.content.MediaDto;

import java.util.List;

public record LifeConsoleHistoryDayDto(
        String date,
        String displayLabel,
        List<MediaDto> selfMedia,
        List<MediaDto> partnerMedia,
        // FR-18: representative location label of the day (latest media's location, nullable)
        String locationLabel
) {
}
