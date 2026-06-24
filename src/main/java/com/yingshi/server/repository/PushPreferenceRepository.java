package com.yingshi.server.repository;

import com.yingshi.server.domain.PushPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushPreferenceRepository extends JpaRepository<PushPreferenceEntity, String> {

    List<PushPreferenceEntity> findByUserId(String userId);

    List<PushPreferenceEntity> findByUserIdIn(Collection<String> userIds);

    Optional<PushPreferenceEntity> findByUserIdAndModuleAndCategory(String userId, String module, String category);
}
