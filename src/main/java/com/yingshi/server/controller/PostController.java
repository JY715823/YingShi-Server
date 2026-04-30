package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.domain.PostMediaDeleteMode;
import com.yingshi.server.dto.content.AddPostMediaRequest;
import com.yingshi.server.dto.content.CreatePostRequest;
import com.yingshi.server.dto.content.PostDetailDto;
import com.yingshi.server.dto.content.UpdatePostCoverRequest;
import com.yingshi.server.dto.content.UpdatePostMediaOrderRequest;
import com.yingshi.server.dto.content.UpdatePostRequest;
import com.yingshi.server.service.content.PostService;
import com.yingshi.server.dto.trash.TrashItemDto;
import com.yingshi.server.service.trash.TrashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@AuthRequired
@Tag(name = "Posts")
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final TrashService trashService;

    public PostController(PostService postService, TrashService trashService) {
        this.postService = postService;
        this.trashService = trashService;
    }

    @Operation(summary = "Get post detail", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailDto> getPost(
            @PathVariable String postId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), postService.getPostDetail(postId, currentUser));
    }

    @Operation(summary = "Create post", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ApiResponse<PostDetailDto> createPost(
            @Valid @RequestBody CreatePostRequest createPostRequest,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), postService.createPost(createPostRequest, currentUser));
    }

    @Operation(summary = "Update post", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{postId}")
    public ApiResponse<PostDetailDto> updatePost(
            @PathVariable String postId,
            @Valid @RequestBody UpdatePostRequest updatePostRequest,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), postService.updatePost(postId, updatePostRequest, currentUser));
    }

    @Operation(summary = "Update post cover", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{postId}/cover")
    public ApiResponse<PostDetailDto> updateCover(
            @PathVariable String postId,
            @Valid @RequestBody UpdatePostCoverRequest updatePostCoverRequest,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), postService.updatePostCover(postId, updatePostCoverRequest, currentUser));
    }

    @Operation(summary = "Update post media order", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{postId}/media-order")
    public ApiResponse<PostDetailDto> updateMediaOrder(
            @PathVariable String postId,
            @Valid @RequestBody UpdatePostMediaOrderRequest updatePostMediaOrderRequest,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), postService.updatePostMediaOrder(postId, updatePostMediaOrderRequest, currentUser));
    }

    @Operation(summary = "Add media to post", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{postId}/media")
    public ApiResponse<PostDetailDto> addMediaToPost(
            @PathVariable String postId,
            @Valid @RequestBody AddPostMediaRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), postService.addMediaToPost(postId, requestBody, currentUser));
    }

    @Operation(summary = "Delete post to trash", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{postId}")
    public ApiResponse<TrashItemDto> deletePost(
            @PathVariable String postId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), trashService.deletePost(postId, currentUser));
    }

    @Operation(summary = "Delete post media", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{postId}/media/{mediaId}")
    public ApiResponse<TrashItemDto> deletePostMedia(
            @PathVariable String postId,
            @PathVariable String mediaId,
            @RequestParam String deleteMode,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                requestId(request),
                trashService.deletePostMedia(postId, mediaId, parseDeleteMode(deleteMode), currentUser)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }

    private PostMediaDeleteMode parseDeleteMode(String deleteMode) {
        try {
            return PostMediaDeleteMode.valueOf(deleteMode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "deleteMode must be directory or system.");
        }
    }
}
