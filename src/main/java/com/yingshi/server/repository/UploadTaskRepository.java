package com.yingshi.server.repository;

import com.yingshi.server.domain.UploadTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadTaskRepository extends JpaRepository<UploadTaskEntity, String> {

    Optional<UploadTaskEntity> findByIdAndSpaceId(String id, String spaceId);
}
