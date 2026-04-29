package com.yingshi.server.repository;

import com.yingshi.server.domain.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<PostEntity, String> {

    Optional<PostEntity> findByIdAndSpaceId(String id, String spaceId);

    List<PostEntity> findBySpaceIdAndIdIn(String spaceId, Collection<String> ids);
}
