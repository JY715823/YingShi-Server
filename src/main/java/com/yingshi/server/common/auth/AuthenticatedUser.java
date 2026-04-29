package com.yingshi.server.common.auth;

public record AuthenticatedUser(
        String userId,
        String account,
        String displayName,
        String spaceId
) {
}
