package com.yingshi.server.repository;

import com.yingshi.server.domain.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {

    Optional<AuthSessionEntity> findByIdAndUserIdAndLibraryId(String id, String userId, String libraryId);
}
