package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerDeletedRowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LedgerDeletedRowRepository extends JpaRepository<LedgerDeletedRowEntity, Long> {

    List<LedgerDeletedRowEntity> findByLibraryIdAndDeletedAtAfter(String libraryId, Instant since);

    @Modifying
    @Query("DELETE FROM LedgerDeletedRowEntity e WHERE e.libraryId = :libraryId AND e.deletedAt < :cutoff")
    void deleteByLibraryIdAndDeletedAtBefore(@Param("libraryId") String libraryId, @Param("cutoff") Instant cutoff);
}
