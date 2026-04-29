package com.yingshi.server.repository;

import com.yingshi.server.domain.SpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<SpaceEntity, String> {
}
