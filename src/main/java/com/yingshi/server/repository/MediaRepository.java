package com.yingshi.server.repository;

import com.yingshi.server.domain.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<MediaEntity, String> {

    Optional<MediaEntity> findByIdAndSpaceId(String id, String spaceId);

    List<MediaEntity> findBySpaceId(String spaceId);

    List<MediaEntity> findBySpaceIdAndIdIn(String spaceId, Collection<String> ids);
}
