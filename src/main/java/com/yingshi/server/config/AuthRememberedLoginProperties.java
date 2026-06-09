package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.remembered-login")
public record AuthRememberedLoginProperties(
        Duration ttl
) {

    public AuthRememberedLoginProperties {
        ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofDays(7) : ttl;
    }
}
