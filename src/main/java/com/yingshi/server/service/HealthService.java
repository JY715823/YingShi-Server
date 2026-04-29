package com.yingshi.server.service;

import com.yingshi.server.dto.health.HealthResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class HealthService {

    private final Environment environment;

    public HealthService(Environment environment) {
        this.environment = environment;
    }

    public HealthResponse getHealth() {
        List<String> activeProfiles = Arrays.stream(environment.getActiveProfiles()).toList();
        return new HealthResponse(
                "UP",
                environment.getProperty("spring.application.name", "yingshi-server"),
                activeProfiles,
                Instant.now()
        );
    }
}
