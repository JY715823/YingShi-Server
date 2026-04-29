package com.yingshi.server.controller;

import com.yingshi.server.common.auth.AuthRequired;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.auth.CurrentUser;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import com.yingshi.server.dto.comment.CommentDto;
import com.yingshi.server.dto.comment.CommentPageResponse;
import com.yingshi.server.dto.comment.CreateCommentRequest;
import com.yingshi.server.dto.comment.UpdateCommentRequest;
import com.yingshi.server.service.comment.CommentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AuthRequired
@Tag(name = "Comments")
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "Get post comments", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<CommentPageResponse> getPostComments(
            @PathVariable String postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), commentService.getPostComments(postId, page, size, currentUser));
    }

    @Operation(summary = "Get media comments", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/media/{mediaId}/comments")
    public ApiResponse<CommentPageResponse> getMediaComments(
            @PathVariable String mediaId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), commentService.getMediaComments(mediaId, page, size, currentUser));
    }

    @Operation(summary = "Create post comment", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<CommentDto> createPostComment(
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), commentService.createPostComment(postId, requestBody, currentUser));
    }

    @Operation(summary = "Create media comment", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/media/{mediaId}/comments")
    public ApiResponse<CommentDto> createMediaComment(
            @PathVariable String mediaId,
            @Valid @RequestBody CreateCommentRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), commentService.createMediaComment(mediaId, requestBody, currentUser));
    }

    @Operation(summary = "Update comment", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/api/comments/{commentId}")
    public ApiResponse<CommentDto> updateComment(
            @PathVariable String commentId,
            @Valid @RequestBody UpdateCommentRequest requestBody,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), commentService.updateComment(commentId, requestBody, currentUser));
    }

    @Operation(summary = "Soft delete comment", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/comments/{commentId}")
    public ApiResponse<CommentDto> deleteComment(
            @PathVariable String commentId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest request
    ) {
        return ApiResponse.success(requestId(request), commentService.deleteComment(commentId, currentUser));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }
}
