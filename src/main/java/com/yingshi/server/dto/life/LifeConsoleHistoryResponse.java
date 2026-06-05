package com.yingshi.server.dto.life;

import java.util.List;

public record LifeConsoleHistoryResponse(
        String zoneId,
        LifeConsoleUserDto currentUser,
        LifeConsoleUserDto partner,
        List<LifeConsoleHistoryDayDto> personDays,
        List<LifeConsoleHistoryDayDto> mealDays,
        List<LifeConsoleBowelHistoryDayDto> bowelDays
) {
}
