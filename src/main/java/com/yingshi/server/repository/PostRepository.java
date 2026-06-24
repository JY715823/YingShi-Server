package com.yingshi.server.repository;

import com.yingshi.server.domain.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<PostEntity, String> {

    Optional<PostEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<PostEntity> findByIdAndLibraryIdAndDeletedAtIsNull(String id, String libraryId);

    Optional<PostEntity> findByLibraryIdAndAlbumIdAndSystemKeyAndDeletedAtIsNull(String libraryId, String albumId, String systemKey);

    List<PostEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<PostEntity> findByLibraryIdAndIdInAndDeletedAtIsNull(String libraryId, Collection<String> ids);

    List<PostEntity> findByLibraryIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(String libraryId);

    List<PostEntity> findByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String libraryId);

    Optional<PostEntity> findFirstByLibraryIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String libraryId);

    Optional<PostEntity> findFirstByLibraryIdOrderByUpdatedAtDesc(String libraryId);

    Optional<PostEntity> findFirstByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNullOrderByUpdatedAtDesc(String libraryId);

    Optional<PostEntity> findFirstByLibraryIdAndSystemKeyIsNullOrderByUpdatedAtDesc(String libraryId);

    Optional<PostEntity> findFirstByLibraryIdAndDeletedAtIsNullAndSystemKeyIsNotNullOrderByUpdatedAtDesc(String libraryId);

    List<PostEntity> findByLibraryIdAndAlbumIdAndDeletedAtIsNullOrderByDisplayTimeMillisDescUpdatedAtDesc(
            String libraryId,
            String albumId
    );
}
