package com.yingshi.server.service.auth;

public record JwtTokenBundle(
        String sessionId,
        String refreshTokenId,
        String accessToken,
        String refreshToken,
        java.time.Instant accessExpireAt,
        java.time.Instant refreshExpireAt,
        long accessTokenExpireAtMillis,
        long refreshTokenExpireAtMillis
) {
}
