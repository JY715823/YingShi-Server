package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.content.AlbumDto;
import com.yingshi.server.dto.content.CreateAlbumRequest;
import com.yingshi.server.dto.content.MoveSmallAlbumsRequest;
import com.yingshi.server.dto.content.PostSummaryDto;
import com.yingshi.server.dto.content.UpdateAlbumRequest;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.service.content.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AuthRequired
@Tag(name = "Albums")
@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @Operation(summary = "Create large album", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ApiResponse<AlbumDto> createAlbum(
            @Valid @RequestBody CreateAlbumRequest createAlbumRequest,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), albumService.createAlbum(createAlbumRequest, currentUser));
    }

    @Operation(summary = "List albums in the shared library", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ApiResponse<List<AlbumDto>> listAlbums(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), albumService.listAlbums(currentUser));
    }

    @Operation(summary = "List small albums under an album", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{albumId}/small-albums")
    public ApiResponse<List<PostSummaryDto>> listAlbumPosts(
            @PathVariable String albumId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), albumService.listAlbumPosts(albumId, currentUser));
    }

    @Operation(summary = "Rename large album", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{albumId}")
    public ApiResponse<AlbumDto> updateAlbum(
            @PathVariable String albumId,
            @Valid @RequestBody UpdateAlbumRequest updateAlbumRequest,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), albumService.updateAlbum(albumId, updateAlbumRequest, currentUser));
    }

    @Operation(summary = "Delete large album", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{albumId}")
    public ApiResponse<TrashItemDto> deleteAlbum(
            @PathVariable String albumId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), albumService.deleteAlbum(albumId, currentUser));
    }

    @Operation(summary = "Move small albums to another large album", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{targetAlbumId}/move-small-albums")
    public ApiResponse<List<PostSummaryDto>> moveSmallAlbums(
            @PathVariable String targetAlbumId,
            @Valid @RequestBody MoveSmallAlbumsRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(requestId(httpRequest), albumService.moveSmallAlbums(targetAlbumId, request, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
