package com.yingshi.server.repository;

import com.yingshi.server.domain.AlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<AlbumEntity, String> {

    List<AlbumEntity> findByLibraryIdOrderByTitleAsc(String libraryId);

    Optional<AlbumEntity> findByIdAndLibraryId(String id, String libraryId);

    Optional<AlbumEntity> findByLibraryIdAndSystemKey(String libraryId, String systemKey);

    List<AlbumEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);
}
