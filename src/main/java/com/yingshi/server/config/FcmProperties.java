package com.yingshi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push.fcm")
public record FcmProperties(
        boolean enabled,
        boolean dryRun,
        String projectId,
        String serviceAccountPath,
        String serviceAccountJsonBase64
) {

    public FcmProperties {
        projectId = trimToNull(projectId);
        serviceAccountPath = trimToNull(serviceAccountPath);
        serviceAccountJsonBase64 = trimToNull(serviceAccountJsonBase64);
    }

    public boolean hasCredentials() {
        return serviceAccountPath != null || serviceAccountJsonBase64 != null;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
