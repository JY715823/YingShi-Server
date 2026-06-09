package com.yingshi.server.service.auth;

public record AuthRememberedLoginGrant(
        String token,
        long expireAtMillis
) {
}
