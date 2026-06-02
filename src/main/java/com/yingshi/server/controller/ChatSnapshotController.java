package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.chat.ChatSnapshotDto;
import com.yingshi.server.dto.chat.UpsertChatSnapshotRequest;
import com.yingshi.server.service.chat.ChatSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AuthRequired
@Tag(name = "Chat")
@RestController
@RequestMapping("/api/chat/imported")
public class ChatSnapshotController {

    private final ChatSnapshotService chatSnapshotService;

    public ChatSnapshotController(ChatSnapshotService chatSnapshotService) {
        this.chatSnapshotService = chatSnapshotService;
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

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
