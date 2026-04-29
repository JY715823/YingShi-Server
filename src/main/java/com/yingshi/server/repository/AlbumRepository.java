package com.yingshi.server.repository;

import com.yingshi.server.domain.AlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<AlbumEntity, String> {

    List<AlbumEntity> findBySpaceIdOrderByTitleAsc(String spaceId);

    Optional<AlbumEntity> findByIdAndSpaceId(String id, String spaceId);

    List<AlbumEntity> findBySpaceIdAndIdIn(String spaceId, Collection<String> ids);
}
