package com.yingshi.server.dto.life;

public record RegisterPushTokenResponse(
        String tokenId,
        String platform,
        Long lastSeenAtMillis,
        boolean enabled
) {
}
