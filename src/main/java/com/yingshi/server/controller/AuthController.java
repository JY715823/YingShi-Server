package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.auth.AuthCurrentUserResponse;
import com.yingshi.server.dto.auth.AuthLoginRequest;
import com.yingshi.server.dto.auth.AuthLoginResponse;
import com.yingshi.server.dto.auth.AuthLogoutRequest;
import com.yingshi.server.dto.auth.AuthLogoutResponse;
import com.yingshi.server.dto.auth.AuthRefreshTokenRequest;
import com.yingshi.server.dto.auth.AuthRefreshTokenResponse;
import com.yingshi.server.dto.auth.AuthUpdateProfileRequest;
import com.yingshi.server.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Account password login")
    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(
            @Valid @RequestBody AuthLoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String requestId = (String) httpServletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, authService.login(request));
    }

    @Operation(summary = "Exchange refresh token")
    @PostMapping("/refresh-token")
    public ApiResponse<AuthRefreshTokenResponse> refreshToken(
            @Valid @RequestBody AuthRefreshTokenRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String requestId = (String) httpServletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, authService.refreshToken(request));
    }

    @AuthRequired
    @Operation(summary = "Current authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ApiResponse<AuthCurrentUserResponse> currentUser(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest httpServletRequest
    ) {
        String requestId = (String) httpServletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, authService.getCurrentUser(currentUser));
    }

    @AuthRequired
    @Operation(summary = "Logout placeholder", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ApiResponse<AuthLogoutResponse> logout(
            @RequestBody(required = false) AuthLogoutRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String requestId = (String) httpServletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, authService.logout());
    }

    @AuthRequired
    @Operation(summary = "Update current user profile", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/me/profile")
    public ApiResponse<AuthCurrentUserResponse> updateCurrentUserProfile(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody AuthUpdateProfileRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String requestId = (String) httpServletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, authService.updateCurrentUserProfile(currentUser, request));
    }

    @AuthRequired
    @Operation(summary = "Upload current user avatar", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AuthCurrentUserResponse> uploadCurrentUserAvatar(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest httpServletRequest
    ) {
        String requestId = (String) httpServletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(requestId, authService.uploadCurrentUserAvatar(currentUser, file));
    }

    @AuthRequired
    @Operation(summary = "Load avatar image", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> getAvatar(
            @PathVariable String userId,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(authService.loadAvatar(userId, currentUser));
    }
}
