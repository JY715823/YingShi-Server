package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.chat.ChatImportedSyncRequest;
import com.yingshi.server.dto.chat.ChatImportedSyncResponse;
import com.yingshi.server.dto.chat.ChatSnapshotDto;
import com.yingshi.server.dto.chat.UpsertChatSnapshotRequest;
import com.yingshi.server.service.chat.ChatImportedSyncService;
import com.yingshi.server.service.chat.ChatImportedZipService;
import com.yingshi.server.service.chat.ChatSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@AuthRequired
@Tag(name = "Chat")
@RestController
@RequestMapping("/api/chat/imported")
public class ChatSnapshotController {

    private final ChatSnapshotService chatSnapshotService;
    private final ChatImportedSyncService chatImportedSyncService;
    private final ChatImportedZipService chatImportedZipService;

    public ChatSnapshotController(ChatSnapshotService chatSnapshotService,
                                  ChatImportedSyncService chatImportedSyncService,
                                  ChatImportedZipService chatImportedZipService) {
        this.chatSnapshotService = chatSnapshotService;
        this.chatImportedSyncService = chatImportedSyncService;
        this.chatImportedZipService = chatImportedZipService;
    }

    @Operation(summary = "Get current chat snapshot", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/snapshot")
    public ApiResponse<ChatSnapshotDto> getSnapshot(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), chatSnapshotService.getSnapshot(currentUser));
    }

    @Operation(summary = "Replace current chat snapshot", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/snapshot")
    public ApiResponse<ChatSnapshotDto> upsertSnapshot(
            @Valid @RequestBody UpsertChatSnapshotRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), chatSnapshotService.upsertSnapshot(requestBody, currentUser));
    }

    @Operation(summary = "Row-level incremental sync for imported chat data", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/sync")
    public ApiResponse<ChatImportedSyncResponse> sync(
            @Valid @RequestBody ChatImportedSyncRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), chatImportedSyncService.sync(requestBody, currentUser.libraryId()));
    }

    @Operation(summary = "Upload and import a QCE ZIP archive", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/upload-zip")
    public ApiResponse<Map<String, Object>> uploadZip(
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        if (file.isEmpty()) {
            return ApiResponse.error(requestId(request),
                    new com.yingshi.server.common.response.ApiError("EMPTY_FILE", "Uploaded file is empty", null));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            return ApiResponse.error(requestId(request),
                    new com.yingshi.server.common.response.ApiError("INVALID_FORMAT", "Only .zip files are accepted", null));
        }

        try {
            byte[] zipBytes = file.getBytes();
            Map<String, Object> stats = chatImportedZipService.importZip(zipBytes, currentUser.libraryId());
            return ApiResponse.success(requestId(request), stats);
        } catch (IOException e) {
            return ApiResponse.error(requestId(request),
                    new com.yingshi.server.common.response.ApiError("READ_ERROR", "Failed to read uploaded file: " + e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(requestId(request),
                    new com.yingshi.server.common.response.ApiError("INVALID_ARCHIVE", e.getMessage(), null));
        } catch (Exception e) {
            return ApiResponse.error(requestId(request),
                    new com.yingshi.server.common.response.ApiError("IMPORT_FAILED", "Import failed: " + e.getMessage(), null));
        }
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
