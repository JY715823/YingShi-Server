package com.yingshi.server.dto.life;

import java.util.List;

public record LifeConsoleBowelUserSummaryDto(
        String userId,
        int count,
        Long latestOccurredAtMillis,
        List<Long> eventTimesMillis
) {
}
