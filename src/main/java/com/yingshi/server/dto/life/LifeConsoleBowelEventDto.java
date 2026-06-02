package com.yingshi.server.dto.life;

public record LifeConsoleBowelEventDto(
        String bowelEventId,
        String userId,
        Long occurredAtMillis
) {
}
