package com.yingshi.server.repository;

import com.yingshi.server.domain.LedgerSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LedgerSnapshotRepository extends JpaRepository<LedgerSnapshotEntity, String> {

    Optional<LedgerSnapshotEntity> findByLibraryId(String libraryId);

    @Query("SELECT MAX(l.updatedAt) FROM LedgerSnapshotEntity l WHERE l.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);
}
