package com.yingshi.server.repository;

import com.yingshi.server.domain.PostAlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostAlbumRepository extends JpaRepository<PostAlbumEntity, String> {

    List<PostAlbumEntity> findBySpaceIdAndAlbumId(String spaceId, String albumId);

    List<PostAlbumEntity> findBySpaceIdAndPostId(String spaceId, String postId);

    List<PostAlbumEntity> findBySpaceIdAndPostIdIn(String spaceId, Collection<String> postIds);
}
