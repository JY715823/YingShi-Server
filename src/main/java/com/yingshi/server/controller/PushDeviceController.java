package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.life.RegisterPushTokenRequest;
import com.yingshi.server.dto.life.RegisterPushTokenResponse;
import com.yingshi.server.service.push.PushNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AuthRequired
@Tag(name = "Push Devices")
@RestController
@RequestMapping("/api/push")
public class PushDeviceController {

    private final PushNotificationService pushNotificationService;

    public PushDeviceController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @Operation(summary = "Register an FCM device token", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/device-tokens")
    public ApiResponse<RegisterPushTokenResponse> registerDeviceToken(
            @Valid @RequestBody RegisterPushTokenRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), pushNotificationService.registerDeviceToken(requestBody, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
