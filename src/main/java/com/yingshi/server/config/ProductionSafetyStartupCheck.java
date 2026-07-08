package com.yingshi.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ProductionSafetyStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyStartupCheck.class);

    private static final List<String> DEFAULT_SECRETS = List.of(
            "change-me-to-a-long-dev-secret-at-least-32-characters",
            "dev-secret-key-for-yingshi-server-minimum-length-32"
    );

    private static final List<String> DEFAULT_PASSWORDS = List.of(
            "yingshi_dev_password",
            "yingshi_minio_access",
            "yingshi_minio_secret"
    );

    private final Environment environment;
    private final AuthProperties authProperties;
    private final StorageProperties storageProperties;

    public ProductionSafetyStartupCheck(
            Environment environment,
            AuthProperties authProperties,
            StorageProperties storageProperties
    ) {
        this.environment = environment;
        this.authProperties = authProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!productionSafetyEnabled()) {
            return;
        }
        rejectDefault("APP_AUTH_JWT_SECRET", authProperties.getSecret(), DEFAULT_SECRETS);
        rejectDefault("SPRING_DATASOURCE_PASSWORD", environment.getProperty("spring.datasource.password"), DEFAULT_PASSWORDS);
        rejectDefault("STORAGE_ACCESS_KEY", storageProperties.accessKey(), DEFAULT_PASSWORDS);
        rejectDefault("STORAGE_SECRET_KEY", storageProperties.secretKey(), DEFAULT_PASSWORDS);
        if (storageProperties.cdnDomain() != null && storageProperties.cdnAuthKey() == null) {
            throw new IllegalStateException("STORAGE_CDN_AUTH_KEY must be configured when CDN is enabled for production.");
        }

        // CORS wildcard check — warn but do not block startup
        String corsOrigins = environment.getProperty("app.cors.allowed-origins", "");
        if (corsOrigins == null || corsOrigins.isBlank()) {
            log.warn("CORS WARNING: app.cors.allowed-origins is empty. "
                    + "All origins are allowed with credentials=true. "
                    + "Set APP_CORS_ALLOWED_ORIGINS for production deployments.");
        }
    }

    private boolean productionSafetyEnabled() {
        if (environment.getProperty("app.production-safety.enabled", Boolean.class, false)) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("prod")
                        || profile.equals("production")
                        || profile.equals("cloud")
                        || profile.equals("tencent"));
    }

    private void rejectDefault(String name, String value, List<String> defaults) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for production.");
        }
        String normalizedValue = value.trim();
        if (defaults.stream().anyMatch(normalizedValue::equals)) {
            throw new IllegalStateException(name + " uses a development default and must be changed for production.");
        }
    }
}
