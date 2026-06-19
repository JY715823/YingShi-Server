package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String provider,
        String bucket,
        String localRoot,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String cdnDomain,
        String cdnAuthKey,
        String cdnSignParam,
        String cdnTimestampParam,
        Duration signedUrlTtl,
        Boolean directUploadEnabled,
        Boolean forcePathStyle,
        String directUploadPublicEndpoint
) {

    public StorageProperties {
        provider = defaultIfBlank(provider, "local");
        bucket = defaultIfBlank(bucket, "yingshi-media");
        localRoot = defaultIfBlank(localRoot, "local-storage");
        endpoint = trimToNull(endpoint);
        region = defaultIfBlank(region, "us-east-1");
        accessKey = trimToNull(accessKey);
        secretKey = trimToNull(secretKey);
        cdnDomain = trimToNull(cdnDomain);
        cdnAuthKey = trimToNull(cdnAuthKey);
        cdnSignParam = defaultIfBlank(cdnSignParam, "sign");
        cdnTimestampParam = defaultIfBlank(cdnTimestampParam, "t");
        signedUrlTtl = signedUrlTtl == null ? Duration.ofMinutes(15) : signedUrlTtl;
        directUploadEnabled = directUploadEnabled != null && directUploadEnabled;
        forcePathStyle = forcePathStyle == null || forcePathStyle;
        directUploadPublicEndpoint = trimToNull(directUploadPublicEndpoint);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
