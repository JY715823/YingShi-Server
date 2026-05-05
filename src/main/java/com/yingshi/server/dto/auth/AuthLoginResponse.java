package com.yingshi.server.dto.auth;

public record AuthLoginResponse(
        String userId,
        String account,
        String displayName,
        String libraryId,
        String libraryDisplayName,
        String accessToken,
        String refreshToken,
        long accessTokenExpireAtMillis,
        long refreshTokenExpireAtMillis
) {
}
