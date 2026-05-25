package com.yingshi.server.dto.auth;

public record AuthRefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpireAtMillis,
        long refreshTokenExpireAtMillis
) {
}
