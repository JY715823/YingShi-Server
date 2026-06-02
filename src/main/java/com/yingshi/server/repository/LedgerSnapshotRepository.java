package com.yingshi.server.repository;

import com.yingshi.server.domain.LedgerSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerSnapshotRepository extends JpaRepository<LedgerSnapshotEntity, String> {

    Optional<LedgerSnapshotEntity> findByLibraryId(String libraryId);
}
