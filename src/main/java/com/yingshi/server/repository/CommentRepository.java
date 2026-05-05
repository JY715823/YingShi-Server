package com.yingshi.server.repository;

import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<CommentEntity, String> {

    Page<CommentEntity> findByLibraryIdAndTargetTypeAndPostId(
            String libraryId,
            CommentTargetType targetType,
            String postId,
            Pageable pageable
    );

    Page<CommentEntity> findByLibraryIdAndTargetTypeAndMediaId(
            String libraryId,
            CommentTargetType targetType,
            String mediaId,
            Pageable pageable
    );

    Optional<CommentEntity> findByIdAndLibraryId(String id, String libraryId);
}
