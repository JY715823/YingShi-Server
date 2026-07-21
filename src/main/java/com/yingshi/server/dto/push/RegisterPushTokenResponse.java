package com.yingshi.server.dto.push;

public record RegisterPushTokenResponse(
        String tokenId,
        String platform,
        Long lastSeenAtMillis,
        boolean enabled
) {
}