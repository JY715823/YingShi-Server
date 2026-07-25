package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerBookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerBookRepository extends JpaRepository<LedgerBookEntity, String> {

    List<LedgerBookEntity> findByLibraryId(String libraryId);

    List<LedgerBookEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerBookEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerBookEntity> findByIdAndLibraryId(String id, String libraryId);

    // Round 3 FR-5: lifeConsoleVersion 改为读取关系表 MAX(updated_at)
    @Query("SELECT MAX(e.updatedAt) FROM LedgerBookEntity e WHERE e.libraryId = :libraryId")
    Optional<Instant> findLatestUpdatedAtByLibraryId(@Param("libraryId") String libraryId);
}
