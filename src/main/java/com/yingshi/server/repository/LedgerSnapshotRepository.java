package com.yingshi.server.repository;

import com.yingshi.server.domain.LedgerSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * @deprecated 旧账本 JSON 快照仓库，对应 {@link LedgerSnapshotEntity}。
 *             读取方将在 Round 3 切换至关系表 Repository。请勿新增对此仓库的引用。
 */
@Deprecated
public interface LedgerSnapshotRepository extends JpaRepository<LedgerSnapshotEntity, String> {

    Optional<LedgerSnapshotEntity> findByLibraryId(String libraryId);

    @Query("SELECT MAX(l.updatedAt) FROM LedgerSnapshotEntity l WHERE l.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);
}
