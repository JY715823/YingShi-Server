package com.yingshi.server.repository;

import com.yingshi.server.domain.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<MediaEntity, String> {

    Optional<MediaEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<MediaEntity> findByIdAndLibraryIdAndDeletedAtIsNull(String id, String libraryId);

    List<MediaEntity> findByLibraryId(String libraryId);

    List<MediaEntity> findByLibraryIdAndDeletedAtIsNull(String libraryId);

    List<MediaEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<MediaEntity> findByLibraryIdAndIdInAndDeletedAtIsNull(String libraryId, Collection<String> ids);
}
