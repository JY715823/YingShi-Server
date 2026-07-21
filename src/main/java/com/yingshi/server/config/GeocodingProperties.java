package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FR-18: 高德反向地理编码配置。
 * 通过环境变量 AMAP_GEOCODING_ENABLED / AMAP_GEOCODING_KEY 注入。
 */
@ConfigurationProperties(prefix = "app.geocoding.amap")
public record GeocodingProperties(
        boolean enabled,
        String key,
        String endpoint,
        long timeoutMillis
) {
    public GeocodingProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://restapi.amap.com/v3/geocode/regeo";
        }
        if (timeoutMillis <= 0) {
            timeoutMillis = 3000;
        }
    }
}
