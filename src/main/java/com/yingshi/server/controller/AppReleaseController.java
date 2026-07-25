package com.yingshi.server.controller;

import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.apprelease.AppReleaseCheckResponse;
import com.yingshi.server.service.AppReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 版本更新检查接口。
 *
 * <p>该接口<b>无需登录</b>：App 启动时（用户尚未登录）就要能检查版本，
 * 否则更新流程会卡在登录之前。
 */
@Tag(name = "App Release")
@RestController
@RequestMapping("/api/app/release")
public class AppReleaseController {

    private final AppReleaseService appReleaseService;

    public AppReleaseController(AppReleaseService appReleaseService) {
        this.appReleaseService = appReleaseService;
    }

    @Operation(summary = "Check for app updates (no auth required)")
    @GetMapping("/check")
    public ApiResponse<AppReleaseCheckResponse> checkForUpdate(
            @RequestParam(name = "platform", defaultValue = "android") String platform,
            @RequestParam(name = "versionCode", required = false) Integer versionCode,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                appReleaseService.checkForUpdate(platform, versionCode)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
