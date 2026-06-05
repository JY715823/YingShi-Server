package com.yingshi.server.dto.life;

import java.util.List;

public record LifeConsoleBowelHistoryDayDto(
        String date,
        String displayLabel,
        List<LifeConsoleBowelUserSummaryDto> users
) {
}
