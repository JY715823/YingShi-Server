package com.yingshi.server.dto.health;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HealthResponse(
        String status,
        String application,
        List<String> activeProfiles,
        Map<String, String> checks,
        Instant serverTime
) {
}
