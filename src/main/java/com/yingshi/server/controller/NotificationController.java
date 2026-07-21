package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.notification.NotificationDto;
import com.yingshi.server.dto.notification.NotificationMarkAllReadResponse;
import com.yingshi.server.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AuthRequired
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "List notifications", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ApiResponse<List<NotificationDto>> listNotifications(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                notificationService.listNotifications(currentUser, limit, cursor)
        );
    }

    @Operation(summary = "Get notification detail", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{notificationId}")
    public ApiResponse<NotificationDto> getNotification(
            @PathVariable String notificationId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                notificationService.getNotification(notificationId, currentUser)
        );
    }

    @Operation(summary = "Mark notification as read", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationDto> markRead(
            @PathVariable String notificationId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                notificationService.markRead(notificationId, currentUser)
        );
    }

    @Operation(summary = "Mark all notifications as read", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/read-all")
    public ApiResponse<NotificationMarkAllReadResponse> markAllRead(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                notificationService.markAllRead(currentUser)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
