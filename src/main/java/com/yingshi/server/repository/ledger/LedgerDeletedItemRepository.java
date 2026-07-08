package com.yingshi.server.repository.ledger;

import com.yingshi.server.domain.ledger.LedgerDeletedItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerDeletedItemRepository extends JpaRepository<LedgerDeletedItemEntity, String> {

    List<LedgerDeletedItemEntity> findByLibraryId(String libraryId);

    List<LedgerDeletedItemEntity> findByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    List<LedgerDeletedItemEntity> findByLibraryIdAndUpdatedAtAfter(String libraryId, Instant since);

    void deleteByLibraryIdAndIdIn(String libraryId, Collection<String> ids);

    Optional<LedgerDeletedItemEntity> findByIdAndLibraryId(String id, String libraryId);
}
