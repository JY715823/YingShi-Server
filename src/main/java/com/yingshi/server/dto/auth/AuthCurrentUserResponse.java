package com.yingshi.server.dto.auth;

public record AuthCurrentUserResponse(
        String userId,
        String account,
        String displayName,
        String avatarUrl,
        String spaceId,
        String spaceDisplayName
) {
}
