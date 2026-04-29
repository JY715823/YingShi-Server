package com.yingshi.server.dto.auth;

public record AuthLoginResponse(
        String userId,
        String account,
        String displayName,
        String spaceId,
        String spaceDisplayName,
        String accessToken,
        String refreshToken,
        long accessTokenExpireAtMillis,
        long refreshTokenExpireAtMillis
) {
}
