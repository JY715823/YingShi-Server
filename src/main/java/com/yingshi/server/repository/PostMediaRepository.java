package com.yingshi.server.repository;

import com.yingshi.server.domain.PostMediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostMediaRepository extends JpaRepository<PostMediaEntity, String> {

    List<PostMediaEntity> findBySpaceIdAndPostIdOrderBySortOrderAsc(String spaceId, String postId);

    List<PostMediaEntity> findBySpaceIdAndPostIdIn(String spaceId, Collection<String> postIds);

    List<PostMediaEntity> findBySpaceIdAndMediaIdIn(String spaceId, Collection<String> mediaIds);

    boolean existsBySpaceIdAndPostIdAndMediaId(String spaceId, String postId, String mediaId);
}
