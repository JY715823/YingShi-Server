package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.upload.UploadCompleteResponse;
import com.yingshi.server.dto.upload.UploadTokenRequest;
import com.yingshi.server.dto.upload.UploadTokenResponse;
import com.yingshi.server.service.upload.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@AuthRequired
@Tag(name = "Uploads")
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "Create upload token", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/token")
    public ApiResponse<UploadTokenResponse> createUploadToken(
            @Valid @RequestBody UploadTokenRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), uploadService.createUploadToken(requestBody, currentUser));
    }

    @Operation(summary = "Upload local file", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{uploadId}/file")
    public ApiResponse<UploadCompleteResponse> uploadFile(
            @PathVariable String uploadId,
            @RequestPart("file") MultipartFile file,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), uploadService.uploadFile(uploadId, file, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
