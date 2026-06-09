package com.yingshi.server.repository;

import com.yingshi.server.domain.AuthRememberedLoginEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthRememberedLoginRepository extends JpaRepository<AuthRememberedLoginEntity, String> {

    Optional<AuthRememberedLoginEntity> findByUserIdAndDeviceId(String userId, String deviceId);
}
