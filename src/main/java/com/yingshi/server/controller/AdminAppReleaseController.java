package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.apprelease.AppReleaseDto;
import com.yingshi.server.service.AppReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * R2-G-12: Admin APK 发布端点。
 *
 * <p>用于管理员发布新版 APK：上传 APK 文件、计算 SHA-256/大小、写入 app_releases 表。
 * 该端点需要登录认证（@AuthRequired）。
 *
 * <p>注意：当前 AuthRequired 注解不区分角色（无 admin 角色基础设施），
 * 任何已认证用户都可访问。后续应扩展 AuthRequired 增加 roles 元素以限制为 ADMIN。
 *
 * <p>该端点与公开的 {@link AppReleaseController}（/api/app/release/check）分离：
 * - 公开端点：无需登录，用于客户端启动时检查更新
 * - 管理端点：需要登录，用于发布新版本
 */
@AuthRequired
@Tag(name = "Admin App Release")
@RestController
@RequestMapping("/api/admin/app-release")
public class AdminAppReleaseController {

    private final AppReleaseService appReleaseService;

    public AdminAppReleaseController(AppReleaseService appReleaseService) {
        this.appReleaseService = appReleaseService;
    }

    /**
     * R2-G-12: 发布新版 APK（事务性）。
     * 事务内：1. 校验 APK 文件 2. 计算 SHA-256/大小 3. 存储 APK 4. 写入 app_releases 表
     *
     * @param apkFile                  上传的 APK 文件
     * @param versionName              用户可见版本名（如 "1.0"、"1.1.0"）
     * @param versionCode              整数版本号
     * @param minSupportedVersionCode 支持的最低客户端 versionCode（可空）
     * @param forceUpdate              是否强制更新
     * @param currentUser              当前登录用户
     * @return 发布记录 DTO
     */
    @Operation(summary = "Publish a new APK release (admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/publish")
    public ApiResponse<AppReleaseDto> publishRelease(
            @RequestParam("apkFile") MultipartFile apkFile,
            @RequestParam("versionName") String versionName,
            @RequestParam("versionCode") Integer versionCode,
            @RequestParam(value = "minSupportedVersionCode", required = false) Integer minSupportedVersionCode,
            @RequestParam(value = "forceUpdate", defaultValue = "false") boolean forceUpdate,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                appReleaseService.publishRelease(apkFile, versionName, versionCode, minSupportedVersionCode, forceUpdate)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
