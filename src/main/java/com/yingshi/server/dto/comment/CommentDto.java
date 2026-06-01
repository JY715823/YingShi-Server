package com.yingshi.server.dto.comment;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CommentDto(
        String commentId,
        String targetType,
        String smallAlbumId,
        String mediaId,
        String authorId,
        String authorName,
        String content,
        Long createdAtMillis,
        Long updatedAtMillis,
        boolean isDeleted
) {
    @JsonIgnore
    public String postId() {
        return smallAlbumId;
    }
}
