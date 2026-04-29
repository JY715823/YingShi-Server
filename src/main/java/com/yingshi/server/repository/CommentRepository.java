package com.yingshi.server.repository;

import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<CommentEntity, String> {

    Page<CommentEntity> findBySpaceIdAndTargetTypeAndPostId(
            String spaceId,
            CommentTargetType targetType,
            String postId,
            Pageable pageable
    );

    Page<CommentEntity> findBySpaceIdAndTargetTypeAndMediaId(
            String spaceId,
            CommentTargetType targetType,
            String mediaId,
            Pageable pageable
    );

    Optional<CommentEntity> findByIdAndSpaceId(String id, String spaceId);
}
