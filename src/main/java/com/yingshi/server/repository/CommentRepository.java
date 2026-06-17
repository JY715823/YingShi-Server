package com.yingshi.server.repository;

import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<CommentEntity, String> {

    Page<CommentEntity> findByLibraryIdAndTargetTypeAndPostIdAndDeletedAtIsNull(
            String libraryId,
            CommentTargetType targetType,
            String postId,
            Pageable pageable
    );

    Page<CommentEntity> findByLibraryIdAndTargetTypeAndMediaIdAndDeletedAtIsNull(
            String libraryId,
            CommentTargetType targetType,
            String mediaId,
            Pageable pageable
    );

    Optional<CommentEntity> findByIdAndLibraryId(String id, String libraryId);

    List<CommentEntity> findByLibraryIdOrderByCreatedAtDesc(String libraryId);

    void deleteByLibraryIdAndPostId(String libraryId, String postId);

    void deleteByLibraryIdAndMediaId(String libraryId, String mediaId);
}
