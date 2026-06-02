package com.yingshi.server.repository;

import com.yingshi.server.domain.PushDeviceTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceTokenEntity, String> {

    Optional<PushDeviceTokenEntity> findByToken(String token);

    List<PushDeviceTokenEntity> findByTokenIn(List<String> tokens);

    List<PushDeviceTokenEntity> findByLibraryIdAndEnabledTrue(String libraryId);
}
