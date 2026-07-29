package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.health.HealthResponse;
import com.yingshi.server.dto.health.PublicHealthResponse;
import com.yingshi.server.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(summary = "Public health check (minimal, no dependencies probed)")
    @GetMapping
    public ApiResponse<PublicHealthResponse> health(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, healthService.getPublicHealth());
    }

    @Operation(summary = "Detailed health check (requires authentication)")
    @AuthRequired
    @GetMapping("/detailed")
    public ApiResponse<HealthResponse> healthDetailed(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, healthService.getHealth());
    }
}
