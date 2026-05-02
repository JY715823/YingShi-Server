package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.content.MediaDto;
import com.yingshi.server.service.content.MediaService;
import com.yingshi.server.service.content.MediaFilePayload;
import com.yingshi.server.service.trash.TrashService;
import com.yingshi.server.dto.trash.TrashItemDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AuthRequired
@Tag(name = "Media")
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;
    private final TrashService trashService;

    public MediaController(MediaService mediaService, TrashService trashService) {
        this.mediaService = mediaService;
        this.trashService = trashService;
    }

    @Operation(summary = "Get deduplicated media feed", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/feed")
    public ApiResponse<List<MediaDto>> getMediaFeed(
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), mediaService.getMediaFeed(currentUser));
    }

    @Operation(summary = "Get local media file", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/files/{mediaId}")
    public ResponseEntity<Resource> getMediaFile(
            @PathVariable String mediaId,
            @RequestParam(defaultValue = "original") String variant,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        MediaFilePayload payload = mediaService.loadMediaFile(mediaId, variant, currentUser);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=2592000, immutable")
                .contentType(MediaType.parseMediaType(payload.mimeType()))
                .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
        if (payload.contentLength() != null && payload.contentLength() >= 0) {
            responseBuilder.contentLength(payload.contentLength());
        }
        if (payload.lastModifiedMillis() != null && payload.lastModifiedMillis() > 0L) {
            responseBuilder.lastModified(payload.lastModifiedMillis());
        }
        return responseBuilder.body(payload.resource());
    }

    @Operation(summary = "System delete media", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{mediaId}")
    public ApiResponse<TrashItemDto> deleteMedia(
            @PathVariable String mediaId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.systemDeleteMedia(mediaId, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
