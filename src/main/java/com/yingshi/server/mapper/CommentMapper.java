package com.yingshi.server.mapper;

import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.comment.CommentDto;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {

    public CommentDto toCommentDto(CommentEntity comment, UserEntity author) {
        return new CommentDto(
                comment.getId(),
                comment.getTargetType().name(),
                comment.getPostId(),
                comment.getMediaId(),
                comment.getAuthorId(),
                author == null ? "Unknown" : author.getDisplayName(),
                comment.getDeletedAt() == null ? comment.getContent() : null,
                comment.getCreatedAt() == null ? null : comment.getCreatedAt().toEpochMilli(),
                comment.getUpdatedAt() == null ? null : comment.getUpdatedAt().toEpochMilli(),
                comment.getDeletedAt() != null
        );
    }
}
