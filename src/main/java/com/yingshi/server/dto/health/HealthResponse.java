package com.yingshi.server.dto.health;

import java.time.Instant;
import java.util.List;

public record HealthResponse(
        String status,
        String application,
        List<String> activeProfiles,
        Instant serverTime
) {
}
