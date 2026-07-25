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

        // R3-SEC-003: CORS must be explicit whitelist in production — empty = block startup
        String corsOrigins = environment.getProperty("app.cors.allowed-origins", "");
        if (corsOrigins == null || corsOrigins.isBlank()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must be explicitly configured for production. "
                    + "Set APP_CORS_ALLOWED_ORIGINS to a comma-separated list of HTTPS origins.");
        }
        for (String origin : corsOrigins.split(",")) {
            String normalized = origin.trim().toLowerCase();
            if (normalized.isBlank() || normalized.contains("*") || !normalized.startsWith("https://")) {
                throw new IllegalStateException(
                        "APP_CORS_ALLOWED_ORIGINS must contain only explicit HTTPS origins without wildcards."
                );
            }
        }

        // R3-OPS-001: Swagger/OpenAPI must be disabled in production
        boolean swaggerEnabled = environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false);
        boolean apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false);
        if (swaggerEnabled || apiDocsEnabled) {
            throw new IllegalStateException(
                    "Swagger/OpenAPI must be disabled in production. "
                    + "Set springdoc.swagger-ui.enabled=false and springdoc.api-docs.enabled=false.");
        }

        // R3-SEC-001: Dev seed must not be enabled in production
        boolean devSeedEnabled = environment.getProperty("app.dev-seed.enabled", Boolean.class, false);
        if (devSeedEnabled) {
            throw new IllegalStateException(
                    "app.dev-seed.enabled must be false in production. "
                    + "Development seed accounts with weak passwords cannot be used in production.");
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
