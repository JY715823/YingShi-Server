package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.common.response.PageInfo;
import com.yingshi.server.dto.upload.UploadCompleteResponse;
import com.yingshi.server.dto.upload.UploadConfirmRequest;
import com.yingshi.server.dto.upload.UploadDismissBatchRequest;
import com.yingshi.server.dto.upload.UploadTaskResponse;
import com.yingshi.server.dto.upload.UploadTokenRequest;
import com.yingshi.server.dto.upload.UploadTokenResponse;
import com.yingshi.server.service.upload.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@AuthRequired
@Tag(name = "Uploads")
@RestController
@RequestMapping(value = "/api/uploads", produces = MediaType.APPLICATION_JSON_VALUE)
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

    @Operation(summary = "List upload history", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ApiResponse<java.util.List<UploadTaskResponse>> listUploads(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String cursor,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        UploadService.UploadHistoryResult result = uploadService.listUploadHistory(
                currentUser,
                state,
                operationType,
                pageSize,
                cursor
        );
        return ApiResponse.success(
                requestId(request),
                result.items(),
                new PageInfo(1, result.items().size(), result.nextCursor(), result.hasMore())
        );
    }

    @Operation(summary = "Upload local file", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(
            value = "/{uploadId}/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ApiResponse<UploadCompleteResponse> uploadFile(
            @PathVariable String uploadId,
            @RequestPart("file") MultipartFile file,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), uploadService.uploadFile(uploadId, file, currentUser));
    }

    @Operation(summary = "Get upload task status", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{uploadId}")
    public ApiResponse<UploadTaskResponse> getUploadTask(
            @PathVariable String uploadId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), uploadService.getUploadTask(uploadId, currentUser));
    }

    @Operation(summary = "Confirm upload task", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{uploadId}/confirm")
    public ApiResponse<UploadTaskResponse> confirmUpload(
            @PathVariable String uploadId,
            @RequestBody(required = false) UploadConfirmRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        UploadConfirmRequest normalizedRequest = requestBody == null
                ? new UploadConfirmRequest(null, null)
                : requestBody;
        return ApiResponse.success(
                requestId(request),
                uploadService.confirmUpload(uploadId, normalizedRequest, currentUser)
        );
    }

    @Operation(summary = "Cancel upload task", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{uploadId}/cancel")
    public ApiResponse<UploadTaskResponse> cancelUpload(
            @PathVariable String uploadId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), uploadService.cancelUpload(uploadId, currentUser));
    }

    @Operation(summary = "Dismiss upload task from transfer center", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{uploadId}/dismiss")
    public ApiResponse<UploadTaskResponse> dismissUpload(
            @PathVariable String uploadId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), uploadService.dismissUpload(uploadId, currentUser));
    }

    @Operation(summary = "Dismiss visible upload tasks by category", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/dismiss-batch")
    public ApiResponse<java.util.List<UploadTaskResponse>> dismissUploadBatch(
            @RequestBody(required = false) UploadDismissBatchRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        UploadDismissBatchRequest normalizedRequest = requestBody == null
                ? new UploadDismissBatchRequest(null, null)
                : requestBody;
        return ApiResponse.success(
                requestId(request),
                uploadService.dismissUploadBatch(currentUser, normalizedRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
