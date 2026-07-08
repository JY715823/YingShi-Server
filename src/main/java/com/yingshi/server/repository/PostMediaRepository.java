package com.yingshi.server.repository;

import com.yingshi.server.domain.PostMediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostMediaRepository extends JpaRepository<PostMediaEntity, String> {

    List<PostMediaEntity> findByLibraryIdAndPostIdOrderBySortOrderAsc(String libraryId, String postId);

    List<PostMediaEntity> findByLibraryIdAndPostIdIn(String libraryId, Collection<String> postIds);

    List<PostMediaEntity> findByLibraryIdAndMediaIdIn(String libraryId, Collection<String> mediaIds);

    boolean existsByLibraryIdAndPostIdAndMediaId(String libraryId, String postId, String mediaId);

    void deleteByLibraryIdAndPostId(String libraryId, String postId);

    void deleteByLibraryIdAndMediaId(String libraryId, String mediaId);

    @Query("SELECT MAX(pm.updatedAt) FROM PostMediaEntity pm WHERE pm.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);
}
