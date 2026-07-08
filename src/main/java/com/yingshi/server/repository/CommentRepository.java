package com.yingshi.server.repository;

import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<CommentEntity, String> {

    @Query("SELECT c FROM CommentEntity c WHERE c.libraryId = :libraryId AND c.targetType = :targetType AND c.postId = :postId AND c.deletedAt IS NULL")
    Page<CommentEntity> findByLibraryIdAndTargetTypeAndPostIdAndDeletedAtIsNull(
            @Param("libraryId") String libraryId,
            @Param("targetType") CommentTargetType targetType,
            @Param("postId") String postId,
            Pageable pageable
    );

    @Query("SELECT c FROM CommentEntity c WHERE c.libraryId = :libraryId AND c.targetType = :targetType AND c.mediaId = :mediaId AND c.deletedAt IS NULL")
    Page<CommentEntity> findByLibraryIdAndTargetTypeAndMediaIdAndDeletedAtIsNull(
            @Param("libraryId") String libraryId,
            @Param("targetType") CommentTargetType targetType,
            @Param("mediaId") String mediaId,
            Pageable pageable
    );

    Optional<CommentEntity> findByIdAndLibraryId(String id, String libraryId);

    @Query("SELECT MAX(c.updatedAt) FROM CommentEntity c WHERE c.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);

    List<CommentEntity> findByLibraryIdOrderByCreatedAtDesc(String libraryId);

    void deleteByLibraryIdAndPostId(String libraryId, String postId);

    void deleteByLibraryIdAndMediaId(String libraryId, String mediaId);
}
