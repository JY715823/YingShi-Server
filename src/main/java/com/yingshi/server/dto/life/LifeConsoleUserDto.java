package com.yingshi.server.dto.life;

public record LifeConsoleUserDto(
        String userId,
        String account,
        String displayName,
        String avatarUrl
) {
}
