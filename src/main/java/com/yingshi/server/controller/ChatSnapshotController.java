package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

    private static final Logger log = LoggerFactory.getLogger(ChatSnapshotController.class);

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

    /** R3-CHAT-002: Maximum upload size for ZIP import (50MB at controller level, service allows up to 500MB unzipped). */
    private static final long MAX_ZIP_UPLOAD_BYTES = 50L * 1024 * 1024;

    @Operation(summary = "Upload and import a QCE ZIP archive", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/upload-zip")
    public ApiResponse<Map<String, Object>> uploadZip(
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        // R3-CHAT-002: Pre-check file size at controller level
        if (file.getSize() > MAX_ZIP_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.CHAT_IMPORT_SIZE_EXCEEDED,
                    "ZIP file exceeds maximum upload size of " + (MAX_ZIP_UPLOAD_BYTES / 1024 / 1024) + "MB");
        }

        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CHAT_IMPORT_EMPTY_FILE,
                    "Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CHAT_IMPORT_INVALID_FORMAT,
                    "Only .zip files are accepted");
        }

        try {
            byte[] zipBytes = file.getBytes();
            Map<String, Object> stats = chatImportedZipService.importZip(zipBytes, currentUser.libraryId());
            return ApiResponse.success(requestId(request), stats);
        } catch (IOException e) {
            log.warn("Failed to read uploaded ZIP file for library={}: {}", currentUser.libraryId(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.CHAT_IMPORT_READ_ERROR,
                    "Failed to read uploaded file");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ZIP archive for library={}: {}", currentUser.libraryId(), e.getMessage());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.CHAT_IMPORT_INVALID_ARCHIVE,
                    "Invalid archive format");
        } catch (Exception e) {
            log.error("Unexpected error during ZIP import for library={}", currentUser.libraryId(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.CHAT_IMPORT_FAILED,
                    "Import failed due to an unexpected error");
        }
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
