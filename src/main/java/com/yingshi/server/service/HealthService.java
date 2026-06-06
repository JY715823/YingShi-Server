package com.yingshi.server.service;

import com.yingshi.server.dto.health.HealthResponse;
import com.yingshi.server.service.storage.ObjectStorageService;
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

    public HealthService(
            Environment environment,
            DataSource dataSource,
            ObjectStorageService objectStorageService
    ) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.objectStorageService = objectStorageService;
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
                Instant.now()
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
            return provider == null || provider.isBlank() || bucket == null || bucket.isBlank()
                    ? "DOWN"
                    : "UP";
        } catch (Exception exception) {
            return "DOWN";
        }
    }
}
