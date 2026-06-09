package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.login-code")
public record AuthLoginCodeProperties(
        Integer length,
        Duration ttl,
        Duration resendCooldown,
        Duration rateLimitWindow,
        Integer maxSendsPerWindow,
        Integer maxAttemptsPerChallenge
) {

    public AuthLoginCodeProperties {
        length = normalizePositive(length, 6);
        ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(5) : ttl;
        resendCooldown = resendCooldown == null || resendCooldown.isNegative() ? Duration.ofSeconds(60) : resendCooldown;
        rateLimitWindow = rateLimitWindow == null || rateLimitWindow.isNegative() || rateLimitWindow.isZero()
                ? Duration.ofMinutes(30)
                : rateLimitWindow;
        maxSendsPerWindow = normalizePositive(maxSendsPerWindow, 5);
        maxAttemptsPerChallenge = normalizePositive(maxAttemptsPerChallenge, 5);
    }

    private static Integer normalizePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
