package com.yingshi.server.repository;

import com.yingshi.server.domain.PostAlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostAlbumRepository extends JpaRepository<PostAlbumEntity, String> {

    List<PostAlbumEntity> findByLibraryIdAndAlbumId(String libraryId, String albumId);

    List<PostAlbumEntity> findByLibraryIdAndPostId(String libraryId, String postId);

    List<PostAlbumEntity> findByLibraryIdAndPostIdIn(String libraryId, Collection<String> postIds);

    void deleteByLibraryIdAndPostId(String libraryId, String postId);
}
