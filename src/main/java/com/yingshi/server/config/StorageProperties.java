package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String provider,
        String bucket,
        String localRoot
) {

    public StorageProperties {
        provider = defaultIfBlank(provider, "local");
        bucket = defaultIfBlank(bucket, "yingshi-media");
        localRoot = defaultIfBlank(localRoot, "local-storage");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
