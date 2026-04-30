package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.trash.PendingCleanupDto;
import com.yingshi.server.dto.trash.TrashDetailDto;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.dto.trash.TrashPageResponse;
import com.yingshi.server.service.trash.TrashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AuthRequired
@Tag(name = "Trash")
@RestController
@RequestMapping("/api/trash")
public class TrashController {

    private final TrashService trashService;

    public TrashController(TrashService trashService) {
        this.trashService = trashService;
    }

    @Operation(summary = "List trash items", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/items")
    public ApiResponse<TrashPageResponse> listTrash(
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.listTrash(itemType, page, size, currentUser));
    }

    @Operation(summary = "Get trash item detail", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/items/{trashItemId}")
    public ApiResponse<TrashDetailDto> getTrashDetail(
            @PathVariable String trashItemId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.getTrashDetail(trashItemId, currentUser));
    }

    @Operation(summary = "Restore trash item", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/items/{trashItemId}/restore")
    public ApiResponse<TrashItemDto> restoreTrashItem(
            @PathVariable String trashItemId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.restoreTrashItem(trashItemId, currentUser));
    }

    @Operation(summary = "Move trash item to pending cleanup", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/items/{trashItemId}/remove")
    public ApiResponse<PendingCleanupDto> moveOutOfTrash(
            @PathVariable String trashItemId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.moveOutOfTrash(trashItemId, currentUser));
    }

    @Operation(summary = "Undo remove from trash", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/items/{trashItemId}/undo-remove")
    public ApiResponse<TrashItemDto> undoRemove(
            @PathVariable String trashItemId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.undoRemove(trashItemId, currentUser));
    }

    @Operation(summary = "List pending cleanup items", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/pending-cleanup")
    public ApiResponse<List<PendingCleanupDto>> getPendingCleanup(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.getPendingCleanup(currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
