package com.yingshi.server.service;

import com.yingshi.server.dto.health.HealthResponse;
import com.yingshi.server.dto.health.PublicHealthResponse;
import com.yingshi.server.service.storage.ObjectStorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthService {

    private final Environment environment;
    private final DataSource dataSource;
    private final ObjectStorageService objectStorageService;
    private final BuildProperties buildProperties;

    public HealthService(
            Environment environment,
            DataSource dataSource,
            ObjectStorageService objectStorageService,
            ObjectProvider<BuildProperties> buildPropertiesProvider
    ) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.objectStorageService = objectStorageService;
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    public PublicHealthResponse getPublicHealth() {
        return new PublicHealthResponse("UP");
    }

    public HealthResponse getHealth() {
        List<String> activeProfiles = Arrays.stream(environment.getActiveProfiles()).toList();
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("database", databaseStatus());
        checks.put("storage", storageStatus());
        boolean allUp = checks.values().stream().allMatch("UP"::equals);
        return new HealthResponse(
                allUp ? "UP" : "DEGRADED",
                environment.getProperty("spring.application.name", "yingshi-server"),
                activeProfiles,
                checks,
                Instant.now(),
                buildProperties != null ? buildProperties.getVersion() : "unknown",
                buildProperties != null ? String.valueOf(buildProperties.getTime()) : "unknown"
        );
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception exception) {
            return "DOWN";
        }
    }

    private String storageStatus() {
        try {
            String provider = objectStorageService.provider();
            String bucket = objectStorageService.bucket();
            if (provider == null || provider.isBlank() || bucket == null || bucket.isBlank()) {
                return "DOWN";
            }
            // FR-10: Actually probe storage connectivity by checking a non-existent key
            // This validates the full chain: credentials, network, bucket access
            objectStorageService.exists("__health_check_probe__");
            return "UP";
        } catch (Exception exception) {
            return "DOWN";
        }
    }
}
