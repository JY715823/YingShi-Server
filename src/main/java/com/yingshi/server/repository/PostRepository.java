package com.yingshi.server.repository;

import com.yingshi.server.domain.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<PostEntity, String> {

    Optional<PostEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<PostEntity> findByIdAndLibraryIdAndDeletedAtIsNull(String id, String libraryId);

    List<PostEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<PostEntity> findByLibraryIdAndIdInAndDeletedAtIsNull(String libraryId, Collection<String> ids);

    List<PostEntity> findByLibraryIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(String libraryId);

    List<PostEntity> findByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String libraryId);

    List<PostEntity> findByLibraryIdAndAlbumIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
            String libraryId,
            String albumId
    );
}
