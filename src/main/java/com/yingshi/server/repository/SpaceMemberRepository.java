package com.yingshi.server.repository;

import com.yingshi.server.domain.SpaceMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceMemberRepository extends JpaRepository<SpaceMemberEntity, String> {

    boolean existsByUserIdAndSpaceId(String userId, String spaceId);
}
