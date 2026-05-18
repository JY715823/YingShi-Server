package com.yingshi.server.dto.auth;

public record AuthPartnerProfileResponse(
        String userId,
        String account,
        String displayName,
        String avatarUrl,
        String bio
) {
}
