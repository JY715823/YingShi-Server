package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.location.TrackPointBatchRequest;
import com.yingshi.server.dto.location.TrackPointBatchResponse;
import com.yingshi.server.dto.location.TrackPointDto;
import com.yingshi.server.service.location.LocationTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V52: 足迹轨迹点。
 * POST 批量上行（客户端采样后同步）；GET 按时间拉取 library 内两人轨迹（足迹地图）。
 */
@AuthRequired
@Tag(name = "LocationTracks")
@RestController
@RequestMapping("/api/location-tracks")
public class LocationTrackController {

    private final LocationTrackService locationTrackService;

    public LocationTrackController(LocationTrackService locationTrackService) {
        this.locationTrackService = locationTrackService;
    }

    @Operation(summary = "Batch upload track points", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ApiResponse<TrackPointBatchResponse> uploadBatch(
            @Valid @RequestBody TrackPointBatchRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                requestId(httpRequest),
                locationTrackService.upsertBatch(request, currentUser)
        );
    }

    @Operation(summary = "List track points since a timestamp (library-scoped)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ApiResponse<List<TrackPointDto>> listSince(
            @RequestParam(required = false) Long sinceMillis,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                requestId(httpRequest),
                locationTrackService.listSince(sinceMillis, currentUser)
        );
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
