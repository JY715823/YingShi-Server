package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String provider,
        String bucket,
        String localRoot,
        String endpoint,
        String region,
        String accessKey,
        String secretKey
) {

    public StorageProperties {
        provider = defaultIfBlank(provider, "local");
        bucket = defaultIfBlank(bucket, "yingshi-media");
        localRoot = defaultIfBlank(localRoot, "local-storage");
        endpoint = trimToNull(endpoint);
        region = defaultIfBlank(region, "us-east-1");
        accessKey = trimToNull(accessKey);
        secretKey = trimToNull(secretKey);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
