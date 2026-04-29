package com.yingshi.server.service.auth;

public record JwtTokenBundle(
        String accessToken,
        String refreshToken,
        long accessTokenExpireAtMillis,
        long refreshTokenExpireAtMillis
) {
}
