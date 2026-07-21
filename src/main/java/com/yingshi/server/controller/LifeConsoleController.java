package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.life.AddBowelEventRequest;
import com.yingshi.server.dto.life.LifeConsoleBowelMutationResponse;
import com.yingshi.server.dto.life.LifeConsoleHistoryResponse;
import com.yingshi.server.dto.life.LifeConsoleMediaRequest;
import com.yingshi.server.dto.life.LifeConsoleTodayResponse;
import com.yingshi.server.dto.life.UpdateLocationRequest;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.service.life.LifeConsoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AuthRequired
@Tag(name = "Life Console")
@RestController
@RequestMapping("/api/life-console")
public class LifeConsoleController {

    private final LifeConsoleService lifeConsoleService;

    public LifeConsoleController(LifeConsoleService lifeConsoleService) {
        this.lifeConsoleService = lifeConsoleService;
    }

    @Operation(summary = "Get today's life console snapshot", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/today")
    public ApiResponse<LifeConsoleTodayResponse> getToday(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String zoneId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), lifeConsoleService.getToday(date, zoneId, currentUser));
    }

    @Operation(summary = "Get life console history", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/history")
    public ApiResponse<LifeConsoleHistoryResponse> getHistory(
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false, defaultValue = "60") Integer limitDays,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                lifeConsoleService.getHistory(zoneId, limitDays, currentUser)
        );
    }

    @Operation(summary = "Attach uploaded media to the current user's life console frame", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/media")
    public ApiResponse<LifeConsoleTodayResponse> addMedia(
            @Valid @RequestBody LifeConsoleMediaRequest requestBody,
            @RequestParam(required = false) String zoneId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), lifeConsoleService.addMedia(requestBody, zoneId, currentUser));
    }

    @Operation(summary = "System delete life console media owned by the current user", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/media/{mediaId}")
    public ApiResponse<TrashItemDto> deleteMedia(
            @PathVariable String mediaId,
            @RequestParam String category,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), lifeConsoleService.deleteMedia(mediaId, category, currentUser));
    }

    @Operation(summary = "Add a bowel event for the current user", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/bowel-events")
    public ApiResponse<LifeConsoleBowelMutationResponse> addBowelEvent(
            @RequestParam(required = false) String zoneId,
            @RequestBody(required = false) AddBowelEventRequest body,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), lifeConsoleService.addBowelEvent(zoneId, body, currentUser));
    }

    @Operation(summary = "Delete the current user's latest bowel event today", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/bowel-events/latest")
    public ApiResponse<LifeConsoleBowelMutationResponse> deleteLatestBowelEvent(
            @RequestParam(required = false) String zoneId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), lifeConsoleService.deleteLatestBowelEvent(zoneId, currentUser));
    }

    @Operation(summary = "Update the location of a life console media owned by the current user", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/media/{mediaId}/location")
    public ApiResponse<LifeConsoleTodayResponse> updateMediaLocation(
            @PathVariable String mediaId,
            @Valid @RequestBody UpdateLocationRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), lifeConsoleService.updateMediaLocation(mediaId, requestBody, currentUser));
    }

    @Operation(summary = "Update the location of a bowel event owned by the current user", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/bowel-events/{eventId}/location")
    public ApiResponse<LifeConsoleBowelMutationResponse> updateBowelEventLocation(
            @PathVariable String eventId,
            @Valid @RequestBody UpdateLocationRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), lifeConsoleService.updateBowelEventLocation(eventId, requestBody, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
