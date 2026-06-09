package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.mail")
public record AuthMailProperties(
        Boolean enabled,
        String host,
        Integer port,
        String username,
        String password,
        String fromAddress,
        String fromName,
        Boolean auth,
        Boolean starttls
) {

    public AuthMailProperties {
        enabled = enabled == null || enabled;
        host = defaultIfBlank(host, "smtp.qq.com");
        port = port == null || port <= 0 ? 587 : port;
        username = trimToNull(username);
        password = trimToNull(password);
        fromAddress = defaultIfBlank(fromAddress, "1085060329@qq.com");
        fromName = defaultIfBlank(fromName, "映世");
        auth = auth == null || auth;
        starttls = starttls == null || starttls;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
