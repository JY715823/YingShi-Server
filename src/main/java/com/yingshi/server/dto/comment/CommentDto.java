package com.yingshi.server.dto.comment;

public record CommentDto(
        String commentId,
        String targetType,
        String postId,
        String mediaId,
        String authorId,
        String authorName,
        String content,
        Long createdAtMillis,
        Long updatedAtMillis,
        boolean isDeleted
) {
}
