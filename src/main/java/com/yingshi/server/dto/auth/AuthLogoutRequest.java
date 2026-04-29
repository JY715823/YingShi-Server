package com.yingshi.server.dto.auth;

public record AuthLogoutRequest(
        String refreshToken
) {
}
