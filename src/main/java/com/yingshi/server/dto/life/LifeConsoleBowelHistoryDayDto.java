package com.yingshi.server.dto.life;

import java.util.List;

public record LifeConsoleBowelHistoryDayDto(
        String date,
        String displayLabel,
        List<LifeConsoleBowelUserSummaryDto> users,
        // FR-18: representative location label of the day (latest event's location, nullable)
        String locationLabel
) {
}
