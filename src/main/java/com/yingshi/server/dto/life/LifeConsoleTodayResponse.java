package com.yingshi.server.dto.life;

public record LifeConsoleTodayResponse(
        String date,
        String zoneId,
        LifeConsoleUserDto currentUser,
        LifeConsoleUserDto partner,
        LifeConsoleMediaSlotDto personSelf,
        LifeConsoleMediaSlotDto personPartner,
        LifeConsoleMediaSlotDto mealSelf,
        LifeConsoleMediaSlotDto mealPartner,
        LifeConsoleBowelSummaryDto bowel
) {
}
