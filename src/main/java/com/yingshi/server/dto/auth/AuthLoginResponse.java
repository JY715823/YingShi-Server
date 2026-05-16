package com.yingshi.server.dto.auth;

public record AuthLoginResponse(
        String userId,
        String account,
        String displayName,
        String avatarUrl,
        String bio,
        String libraryId,
        String libraryDisplayName,
        long createdAtMillis,
        long updatedAtMillis,
        String accessToken,
        String refreshToken,
        long accessTokenExpireAtMillis,
        long refreshTokenExpireAtMillis
) {
}
