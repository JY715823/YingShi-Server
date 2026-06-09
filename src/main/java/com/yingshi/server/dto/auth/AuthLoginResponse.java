package com.yingshi.server.dto.auth;

public record AuthLoginResponse(
        String userId,
        String account,
        String displayName,
        String avatarUrl,
        String bio,
        String libraryId,
        String libraryDisplayName,
        AuthPartnerProfileResponse partner,
        long createdAtMillis,
        long updatedAtMillis,
        String rememberedLoginToken,
        Long rememberedLoginExpireAtMillis,
        String accessToken,
        String refreshToken,
        long accessTokenExpireAtMillis,
        long refreshTokenExpireAtMillis
) {
}
