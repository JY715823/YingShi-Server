package com.yingshi.server.repository;

import com.yingshi.server.domain.PostMediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostMediaRepository extends JpaRepository<PostMediaEntity, String> {

    List<PostMediaEntity> findByLibraryIdAndPostIdOrderBySortOrderAsc(String libraryId, String postId);

    List<PostMediaEntity> findByLibraryIdAndPostIdIn(String libraryId, Collection<String> postIds);

    List<PostMediaEntity> findByLibraryIdAndMediaIdIn(String libraryId, Collection<String> mediaIds);

    boolean existsByLibraryIdAndPostIdAndMediaId(String libraryId, String postId, String mediaId);

    void deleteByLibraryIdAndPostId(String libraryId, String postId);

    void deleteByLibraryIdAndMediaId(String libraryId, String mediaId);
}
