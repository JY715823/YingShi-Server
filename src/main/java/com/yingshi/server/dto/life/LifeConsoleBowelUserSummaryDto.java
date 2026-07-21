package com.yingshi.server.dto.life;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LifeConsoleBowelUserSummaryDto(
        String userId,
        int count,
        Long latestOccurredAtMillis,
        List<Long> eventTimesMillis,
        // FR-18: location of the latest bowel event (nullable)
        String latestLocationLabel,
        // Round 7: 当日所有大便事件列表（含完整位置信息），用于今日页大便页每条单独展示
        List<LifeConsoleBowelEventDto> events
) {
    /**
     * 向后兼容构造器：保留旧字段，events 默认 null（旧调用方不传 events）。
     */
    public LifeConsoleBowelUserSummaryDto(
            String userId,
            int count,
            Long latestOccurredAtMillis,
            List<Long> eventTimesMillis,
            String latestLocationLabel
    ) {
        this(userId, count, latestOccurredAtMillis, eventTimesMillis, latestLocationLabel, null);
    }
}
