package com.yingshi.server.repository;

import com.yingshi.server.domain.ChatSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSnapshotRepository extends JpaRepository<ChatSnapshotEntity, String> {
    Optional<ChatSnapshotEntity> findByLibraryId(String libraryId);
}
